package dev.nateriver.territorio;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/** Event-level regression checks; a real server must still validate circuit propagation. */
class RaidMechanismRegressionTest {
    private ServerMock server;
    private TerritorioPlugin plugin;
    private WorldMock world;
    private PlayerMock owner, invader, outsider;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("raid-mechanisms");
        plugin = MockBukkit.load(TerritorioPlugin.class);
        owner = server.addPlayer("Proprietario");
        // MockBukkit has no ray tracing; keep only the mining target deterministic.
        invader = new PlayerMock(server, "Invasor") {
            @Override public Block getTargetBlockExact(int distance) { return world.getBlockAt(8, 64, 8); }
        };
        server.addPlayer(invader);
        outsider = server.addPlayer("Terceiro");
        for (PlayerMock player : List.of(owner, invader, outsider))
            player.teleport(new Location(world, 8, 65, 8));
        plugin.domain().claim(owner.getUniqueId(), new Domain.Chunk(world.getUID(), 0, 0), false);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void startRaid() throws Exception {
        plugin.raids().start(invader);
        plugin.storageCheckpoint().get(5, java.util.concurrent.TimeUnit.SECONDS);
        server.getScheduler().performOneTick();
        assertTrue(plugin.raids().canRaid(invader.getUniqueId(), plugin.domain().claim(
            new Domain.Chunk(world.getUID(), 0, 0))));
    }

    private Block block(Material material) {
        Block block = world.getBlockAt(8, 64, 8);
        block.setType(material);
        return block;
    }

    private PlayerInteractEvent interact(PlayerMock player, Block block, Action action, Material held) {
        var event = new PlayerInteractEvent(player, action, new ItemStack(held), block,
            BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void assertTrigger(PlayerMock player, Block block, Action action, boolean allowed) {
        var event = interact(player, block, action, Material.AIR);
        assertEquals(!allowed, event.useInteractedBlock() == Event.Result.DENY,
            player.getName() + " at " + block.getType());
    }

    @Test void onlyOwnerAndActiveInvaderCanUseButtonsLeversAndPressurePlates() throws Exception {
        startRaid();
        for (Material material : List.of(Material.OAK_BUTTON, Material.LEVER, Material.STONE_PRESSURE_PLATE)) {
            Block trigger = block(material);
            Action action = material == Material.STONE_PRESSURE_PLATE ? Action.PHYSICAL : Action.RIGHT_CLICK_BLOCK;
            assertTrigger(owner, trigger, action, true);
            assertTrigger(invader, trigger, action, true);
            assertTrigger(outsider, trigger, action, false);
        }
        plugin.raids().stopAll();
        assertTrigger(invader, block(Material.LEVER), Action.RIGHT_CLICK_BLOCK, false);
    }

    @Test void manualDoorsRemainRestrictedWhileTheirElectricalSignalIsAllowed() throws Exception {
        startRaid();
        for (Material material : List.of(Material.OAK_DOOR, Material.OAK_TRAPDOOR)) {
            Block door = block(material);
            assertTrigger(owner, door, Action.RIGHT_CLICK_BLOCK, true);
            assertTrigger(invader, door, Action.RIGHT_CLICK_BLOCK, false);
            assertTrigger(outsider, door, Action.RIGHT_CLICK_BLOCK, false);
            var signal = new BlockRedstoneEvent(door, 0, 15);
            server.getPluginManager().callEvent(signal);
            assertEquals(15, signal.getNewCurrent(), "Approved D1-B permits electrical door actuation");
        }
    }

    @Test void triggerPermissionDoesNotAllowBucketsIgnitionOrFarmlandDamage() throws Exception {
        startRaid();
        for (Material held : List.of(Material.WATER_BUCKET, Material.FLINT_AND_STEEL, Material.BONE_MEAL)) {
            var event = interact(invader, block(Material.LEVER), Action.RIGHT_CLICK_BLOCK, held);
            assertNotEquals(Event.Result.DENY, event.useInteractedBlock());
            assertEquals(Event.Result.DENY, event.useItemInHand(), held.name());
        }
        assertTrigger(invader, block(Material.FARMLAND), Action.PHYSICAL, false);
    }

    @Test void previousTrustDoesNotGiveActiveInvaderUnrestrictedConstruction() throws Exception {
        plugin.domain().trust(owner.getUniqueId(), invader.getUniqueId(), true);
        startRaid();
        assertTrigger(invader, block(Material.LEVER), Action.RIGHT_CLICK_BLOCK, true);
        assertTrigger(invader, block(Material.OAK_DOOR), Action.RIGHT_CLICK_BLOCK, false);
        assertTrigger(invader, block(Material.CHEST), Action.RIGHT_CLICK_BLOCK, true);
        assertTrigger(outsider, block(Material.CHEST), Action.RIGHT_CLICK_BLOCK, false);
    }

    @Test void projectileTriggersRequireAnIdentifiedAuthorizedShooter() throws Exception {
        startRaid();
        Block plate = block(Material.OAK_PRESSURE_PLATE);
        for (PlayerMock shooter : List.of(owner, invader, outsider)) {
            var arrow = new ArrowMock(server, UUID.randomUUID());
            arrow.setShooter(shooter);
            var event = new EntityInteractEvent(arrow, plate);
            server.getPluginManager().callEvent(event);
            assertEquals(shooter == outsider, event.isCancelled(), shooter.getName());
        }
        var unknown = new EntityInteractEvent(new ArrowMock(server, UUID.randomUUID()), plate);
        server.getPluginManager().callEvent(unknown);
        assertTrue(unknown.isCancelled());
    }

    @Test void signalTransitionsAreAllowedWithoutUnfreezingDestructivePhysics() throws Exception {
        startRaid();
        for (Material material : List.of(Material.LEVER, Material.REDSTONE_WIRE, Material.REPEATER)) {
            Block circuit = block(material);
            var rising = new BlockRedstoneEvent(circuit, 0, 15);
            server.getPluginManager().callEvent(rising);
            assertEquals(15, rising.getNewCurrent());
            var falling = new BlockRedstoneEvent(circuit, 15, 0);
            server.getPluginManager().callEvent(falling);
            assertEquals(0, falling.getNewCurrent());
        }
        Block sand = block(Material.SAND);
        var fallingSand = new BlockPhysicsEvent(sand, Material.AIR.createBlockData(), sand.getRelative(BlockFace.DOWN));
        server.getPluginManager().callEvent(fallingSand);
        assertTrue(fallingSand.isCancelled());
    }

    /** MockBukkit does not simulate support physics; provide only the native API support query. */
    private Block wireWithSupport(boolean supported) {
        Block actual = block(Material.REDSTONE_WIRE);
        BlockData nativeData = actual.getBlockData();
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
            new Class<?>[]{RedstoneWire.class}, (proxy, method, args) -> {
                if (method.getName().equals("isSupported")) return supported;
                try { return method.invoke(nativeData, args); }
                catch (InvocationTargetException exception) { throw exception.getCause(); }
            });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getBlockData")) return data;
                try { return method.invoke(actual, args); }
                catch (InvocationTargetException exception) { throw exception.getCause(); }
            });
    }

    @Test void onlySupportedSignalComponentsReceivePhysicsAndClaimBordersRemainProtected() throws Exception {
        startRaid();
        for (boolean supported : List.of(true, false)) {
            Block wire = wireWithSupport(supported);
            var physics = new BlockPhysicsEvent(wire, Material.STONE.createBlockData(), world.getBlockAt(8, 63, 8));
            server.getPluginManager().callEvent(physics);
            assertEquals(!supported, physics.isCancelled());
        }
        var boundary = new BlockPhysicsEvent(wireWithSupport(true), Material.STONE.createBlockData(), world.getBlockAt(16, 63, 8));
        server.getPluginManager().callEvent(boundary);
        assertTrue(boundary.isCancelled());
    }

    private BlockDispenseEvent dispense(Block source, Material item) {
        var event = new BlockDispenseEvent(source, new ItemStack(item), new Vector(1, 0, 0));
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test void raidDispensersCanShootSafeProjectilesButCannotEjectItemsOrModifyBlocks() throws Exception {
        startRaid();
        Block dispenser = block(Material.DISPENSER);
        Directional direction = (Directional) dispenser.getBlockData();
        direction.setFacing(BlockFace.EAST);
        dispenser.setBlockData(direction);
        for (Material item : List.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.SNOWBALL, Material.EGG))
            assertFalse(dispense(dispenser, item).isCancelled(), item.name());
        for (Material item : List.of(Material.STONE, Material.TNT, Material.FIRE_CHARGE, Material.WATER_BUCKET,
                Material.LAVA_BUCKET, Material.BONE_MEAL, Material.WIND_CHARGE))
            assertTrue(dispense(dispenser, item).isCancelled(), item.name());
        assertTrue(dispense(block(Material.DROPPER), Material.ARROW).isCancelled());
    }

    @Test void onlyDispenserTntReceivesTheOwnershipBoundaryExceptionOutsideRaids() throws Exception {
        Block source = world.getBlockAt(15, 64, 8);
        for (Material mechanism : List.of(Material.DISPENSER, Material.DROPPER)) {
            source.setType(mechanism);
            Directional direction = (Directional) source.getBlockData();
            direction.setFacing(BlockFace.EAST);
            source.setBlockData(direction);
            assertEquals(mechanism != Material.DISPENSER, dispense(source, Material.TNT).isCancelled());
            assertTrue(dispense(source, Material.DIAMOND).isCancelled());
        }
        startRaid();
        source.setType(Material.DISPENSER);
        Directional direction = (Directional) source.getBlockData();
        direction.setFacing(BlockFace.EAST);
        source.setBlockData(direction);
        assertTrue(dispense(source, Material.TNT).isCancelled());
        assertTrue(dispense(source, Material.ARROW).isCancelled(), "Projectile whitelist does not open claim boundaries");
    }

    @Test void crafterCannotEjectCraftedItemsDuringRaidAndResumesAfterwards() throws Exception {
        Block crafter = world.getBlockAt(5, 64, 5);
        crafter.setType(Material.CRAFTER);
        ItemStack result = new ItemStack(Material.DIAMOND_BLOCK);
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "raid_crafter_regression"), result)
            .shape("DDD", "DDD", "DDD").setIngredient('D', Material.DIAMOND);
        var before = new CrafterCraftEvent(crafter, recipe, result);
        server.getPluginManager().callEvent(before);
        assertFalse(before.isCancelled());
        startRaid();
        var during = new CrafterCraftEvent(crafter, recipe, result);
        server.getPluginManager().callEvent(during);
        assertTrue(during.isCancelled(), "Crafter ejection uses its own event, rather than BlockDispenseEvent");
        assertEquals(0, plugin.domain().s.raids.getLast().blocks);
        plugin.raids().stopAll();
        var after = new CrafterCraftEvent(crafter, recipe, result);
        server.getPluginManager().callEvent(after);
        assertFalse(after.isCancelled());
    }

    @Test void pistonsAndAutomaticInventoryTransfersRemainBlockedOnlyDuringRaid() throws Exception {
        Block piston = block(Material.PISTON);
        Block moved = world.getBlockAt(9, 64, 8);
        moved.setType(Material.STONE);
        Block source = world.getBlockAt(5, 64, 5), destination = world.getBlockAt(6, 64, 5);
        source.setType(Material.CHEST);
        destination.setType(Material.HOPPER);
        var chest = ((Chest) source.getState()).getInventory();
        var hopper = ((Hopper) destination.getState()).getInventory();
        var before = new BlockPistonExtendEvent(piston, List.of(moved), BlockFace.EAST);
        server.getPluginManager().callEvent(before);
        assertFalse(before.isCancelled());
        var normalTransfer = new InventoryMoveItemEvent(chest, new ItemStack(Material.DIAMOND), hopper, false);
        server.getPluginManager().callEvent(normalTransfer);
        assertFalse(normalTransfer.isCancelled());
        startRaid();
        var during = new BlockPistonExtendEvent(piston, List.of(moved), BlockFace.EAST);
        server.getPluginManager().callEvent(during);
        assertTrue(during.isCancelled());
        var raidTransfer = new InventoryMoveItemEvent(chest, new ItemStack(Material.DIAMOND), hopper, false);
        server.getPluginManager().callEvent(raidTransfer);
        assertTrue(raidTransfer.isCancelled());
    }

    @Test void usingMechanismsDoesNotExpandTheThirtyBlockBudgetOrDropAttachedBlocks() throws Exception {
        startRaid();
        Block stone = block(Material.STONE);
        Block torch = stone.getRelative(BlockFace.UP);
        torch.setType(Material.TORCH);
        var supportBreak = new BlockBreakEvent(stone, invader);
        server.getPluginManager().callEvent(supportBreak);
        assertTrue(supportBreak.isCancelled());
        assertEquals(0, plugin.domain().s.raids.getLast().blocks);
        torch.setType(Material.AIR);
        for (int i = 0; i < 30; i++) {
            var breaking = new BlockBreakEvent(stone, invader);
            server.getPluginManager().callEvent(breaking);
            assertFalse(breaking.isCancelled(), "Allowed block " + i);
        }
        var excess = new BlockBreakEvent(stone, invader);
        server.getPluginManager().callEvent(excess);
        assertTrue(excess.isCancelled());
        assertEquals(30, plugin.domain().s.raids.getLast().blocks);
        assertTrigger(invader, block(Material.LEVER), Action.RIGHT_CLICK_BLOCK, true);
        assertTrigger(invader, block(Material.CHEST), Action.RIGHT_CLICK_BLOCK, true);
        assertTrigger(outsider, block(Material.LEVER), Action.RIGHT_CLICK_BLOCK, false);
        assertEquals(30, plugin.domain().s.raids.getLast().blocks);
    }
}
