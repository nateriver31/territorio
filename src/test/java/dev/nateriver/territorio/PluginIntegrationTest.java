package dev.nateriver.territorio;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.bukkit.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import static org.junit.jupiter.api.Assertions.*;

/** Uses MockBukkit 26.2; does not claim validation of a live Minecraft client. */
class PluginIntegrationTest {
    ServerMock server; TerritorioPlugin plugin; WorldMock world; PlayerMock a,b;
    @BeforeEach void setup() {
        server=MockBukkit.mock();world=server.addSimpleWorld("test");
        plugin=MockBukkit.load(TerritorioPlugin.class);
        assertTrue(plugin.isEnabled());assertTrue(plugin.operational());
        a=server.addPlayer("Dono");
        // MockBukkit has no ray tracing. Stub only sight; test all grants/events through real plugin code.
        b=new PlayerMock(server,"Visitante") { @Override public org.bukkit.block.Block getTargetBlockExact(int distance) {return world.getBlockAt(8,64,8);} };server.addPlayer(b);
        a.teleport(new Location(world,8,65,8));b.teleport(new Location(world,8,65,8));
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void commandCreatesGroupAndClaimsWithPermissions() {
        assertTrue(a.performCommand("t criar ROMA Império Romano"));
        assertEquals("ROMA",plugin.domain().group(a.getUniqueId()).tag);
        assertTrue(a.performCommand("t claim"));
        assertEquals(Domain.Kind.GROUP,plugin.domain().claim(new Domain.Chunk(world.getUID(),0,0)).kind);
        assertFalse(a.hasPermission("territorio.admin.bypass"));
    }
    @Test void protectedBreakAndMechanismsDenyOutsiderButAllowOwner() {
        a.performCommand("claim");var block=world.getBlockAt(8,64,8);block.setType(Material.STONE);
        BlockBreakEvent outsider=new BlockBreakEvent(block,b);server.getPluginManager().callEvent(outsider);assertTrue(outsider.isCancelled());
        BlockBreakEvent owner=new BlockBreakEvent(block,a);server.getPluginManager().callEvent(owner);assertFalse(owner.isCancelled());
        block.setType(Material.OAK_DOOR);
        PlayerInteractEvent door=new PlayerInteractEvent(b,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.AIR),block,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        server.getPluginManager().callEvent(door);assertEquals(org.bukkit.event.Event.Result.DENY,door.useInteractedBlock());
    }
    @Test void peaceNotifiesReceivingGroupAndCanBeRefusedThenAccepted() {
        a.performCommand("t criar ROMA Império Romano");b.performCommand("t criar VIK Vikings");
        a.performCommand("t guerra declarar VIK");
        while(b.nextMessage()!=null){};
        a.performCommand("t paz propor VIK");
        String received=b.nextMessage();assertNotNull(received);assertTrue(received.contains("propôs paz"));assertTrue(received.contains("ROMA"));
        b.performCommand("t paz recusar ROMA");assertTrue(plugin.domain().inWar(plugin.domain().group(a.getUniqueId()).id));
        a.performCommand("t paz propor VIK");b.performCommand("t paz aceitar ROMA");
        assertFalse(plugin.domain().inWar(plugin.domain().group(a.getUniqueId()).id));
    }
    @Test void hopperAndPistonCannotCrossOwnershipBoundary() {
        a.performCommand("claim");
        var piston=world.getBlockAt(15,64,8);piston.setType(Material.PISTON);
        var stone=world.getBlockAt(16,64,8);stone.setType(Material.STONE);
        BlockPistonExtendEvent event=new BlockPistonExtendEvent(piston,java.util.List.of(stone),org.bukkit.block.BlockFace.EAST);
        server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());
    }

    @Test void raidGrantsOnlyOneInvaderAndRevokesOnShutdownWithCooldowns() throws Exception {
        a.performCommand("claim");var block=world.getBlockAt(8,64,8);block.setType(Material.STONE);
        PlayerMock third=server.addPlayer("Terceiro");third.teleport(b.getLocation());
        plugin.raids().start(b);
        plugin.storageCheckpoint().get(5,java.util.concurrent.TimeUnit.SECONDS);server.getScheduler().performOneTick();
        Domain.Claim claim=plugin.domain().claim(new Domain.Chunk(world.getUID(),0,0));
        assertTrue(plugin.raids().canRaid(b.getUniqueId(),claim));
        assertFalse(plugin.raids().canRaid(third.getUniqueId(),claim));
        assertThrows(IllegalArgumentException.class,()->plugin.raids().start(third));
        assertThrows(IllegalArgumentException.class,()->plugin.domain().create(a.getUniqueId(),"Escapar","ESC"));
        assertTrue(plugin.raids().canBreak(b.getUniqueId(),block));
        for(int i=0;i<30;i++)plugin.raids().markBroken(b.getUniqueId(),block);
        assertFalse(plugin.raids().canBreak(b.getUniqueId(),block));
        assertTrue(plugin.raids().canOpen(b.getUniqueId(),claim));
        plugin.raids().stopAll();
        assertFalse(plugin.raids().canOpen(b.getUniqueId(),claim));
        assertTrue(plugin.domain().s.victimCooldown.get(a.getUniqueId())>System.currentTimeMillis());
        assertTrue(plugin.domain().s.raiderCooldown.get(b.getUniqueId())>System.currentTimeMillis());
        assertEquals(30,plugin.domain().s.raids.getFirst().blocks);
        assertEquals("REINICIO",plugin.domain().s.raids.getFirst().result);
    }
    @SuppressWarnings("deprecation")
    @Test void playerVersusPlayerDamageIsNeverCancelledByTerritory() {
        a.performCommand("claim");
        EntityDamageByEntityEvent damage=new EntityDamageByEntityEvent(b,a,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4.0);
        server.getPluginManager().callEvent(damage);assertFalse(damage.isCancelled());assertEquals(4.0,damage.getDamage());
    }

    @Test void corruptStateEntersMaintenanceAndIsNeverOverwritten() throws Exception {
        java.nio.file.Path state=plugin.getDataFolder().toPath().resolve("state.json");
        server.getPluginManager().disablePlugin(plugin);
        java.nio.file.Files.writeString(state,"{INVALID STATE");
        server.getPluginManager().enablePlugin(plugin);
        assertTrue(plugin.isEnabled());assertFalse(plugin.operational());
        BlockBreakEvent breakEvent=new BlockBreakEvent(world.getBlockAt(8,64,8),a);
        server.getPluginManager().callEvent(breakEvent);assertTrue(breakEvent.isCancelled());
        server.getPluginManager().disablePlugin(plugin);
        assertEquals("{INVALID STATE",java.nio.file.Files.readString(state));
    }

    @Test void normalRestartPreservesCooldownsAndClaimsButNeverRestoresRaidGrants() throws Exception {
        a.performCommand("claim");world.getBlockAt(8,64,8).setType(Material.STONE);
        plugin.raids().start(b);plugin.storageCheckpoint().get(5,java.util.concurrent.TimeUnit.SECONDS);server.getScheduler().performOneTick();
        Domain.Chunk at=new Domain.Chunk(world.getUID(),0,0);
        assertTrue(plugin.raids().canRaid(b.getUniqueId(),plugin.domain().claim(at)));
        long victimUntil=plugin.domain().s.victimCooldown.get(a.getUniqueId()),invaderUntil=plugin.domain().s.raiderCooldown.get(b.getUniqueId());
        server.getPluginManager().disablePlugin(plugin);server.getPluginManager().enablePlugin(plugin);
        assertTrue(plugin.operational());assertNotNull(plugin.domain().claim(at));
        assertFalse(plugin.raids().canRaid(b.getUniqueId(),plugin.domain().claim(at)));
        assertEquals(victimUntil,plugin.domain().s.victimCooldown.get(a.getUniqueId()));
        assertEquals(invaderUntil,plugin.domain().s.raiderCooldown.get(b.getUniqueId()));
        assertEquals("REINICIO",plugin.domain().s.raids.getFirst().result);
    }
}
