package dev.nateriver.territorio;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/** Tests actual event handlers; entity proxies supply API fields MockBukkit does not simulate. */
class ExplosionFireRegressionTest {
    ServerMock server;
    TerritorioPlugin plugin;
    WorldMock world;
    PlayerMock owner, visitor, outsider;
    Block inside, wilderness;

    @BeforeEach void setup() {
        server=MockBukkit.mock();
        world=server.addSimpleWorld("explosion-tests");
        plugin=MockBukkit.load(TerritorioPlugin.class);
        owner=server.addPlayer("Dono");
        visitor=new PlayerMock(server,"Visitante") {
            @Override public Block getTargetBlockExact(int distance) {return world.getBlockAt(8,64,8);}
        };
        server.addPlayer(visitor);
        outsider=server.addPlayer("Terceiro");
        for (PlayerMock player:List.of(owner,visitor,outsider)) player.teleport(new Location(world,8,65,8));
        inside=world.getBlockAt(8,64,8);inside.setType(Material.STONE);
        wilderness=world.getBlockAt(72,64,8);wilderness.setType(Material.STONE);
        plugin.domain().claim(owner.getUniqueId(),chunk(inside),false);
    }

    @AfterEach void cleanup() {MockBukkit.unmock();}

    Domain.Chunk chunk(Block block) {return new Domain.Chunk(world.getUID(),block.getX()>>4,block.getZ()>>4);}

    @SuppressWarnings("unchecked")
    <T extends Entity> T entity(Class<T> api, EntityType type, Map<String,Object> fields) {
        UUID id=UUID.randomUUID();
        return (T)Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args)-> {
            if (fields.containsKey(method.getName())) return fields.get(method.getName());
            return switch (method.getName()) {
                case "getType" -> type;
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> inside.getLocation();
                case "isValid" -> true;
                case "toString" -> type+" test entity";
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                default -> {
                    if (method.getReturnType()==boolean.class) yield false;
                    throw new UnsupportedOperationException("Unstubbed entity API: "+method.getName());
                }
            };
        });
    }

    TNTPrimed tnt(Entity source) {
        Map<String,Object> fields=new java.util.HashMap<>();fields.put("getSource",source);
        return entity(TNTPrimed.class,EntityType.TNT,fields);
    }

    EntityExplodeEvent explode(Entity source, Block... blocks) {
        EntityExplodeEvent event=new EntityExplodeEvent(source,inside.getLocation(),
                new ArrayList<>(List.of(blocks)),0.7F,ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled(),"Filtering blocks must preserve the explosion itself");
        assertEquals(0.7F,event.getYield());
        return event;
    }

    @Test void directTntChecksEachClaimAndPreservesWildernessAndEffects() {
        assertEquals(List.of(wilderness),explode(tnt(visitor),inside,wilderness).blockList());
        assertEquals(List.of(inside,wilderness),explode(tnt(owner),inside,wilderness).blockList());
        plugin.domain().trust(owner.getUniqueId(),visitor.getUniqueId(),true);
        assertEquals(List.of(inside),explode(tnt(visitor),inside).blockList());
        plugin.domain().trust(owner.getUniqueId(),visitor.getUniqueId(),false);
        assertTrue(explode(tnt(visitor),inside).blockList().isEmpty());
    }

    @Test void groupMembershipAndExplicitBuildGrantWorkButMereAllianceDoesNot() {
        Domain domain=plugin.domain();
        Domain.Group home=domain.create(owner.getUniqueId(),"Roma","ROMA");
        domain.invite(owner.getUniqueId(),outsider.getUniqueId());domain.accept(outsider.getUniqueId());
        assertEquals(List.of(inside),explode(tnt(outsider),inside).blockList());
        Domain.Group ally=domain.create(visitor.getUniqueId(),"Vikings","VIK");
        domain.ally(owner.getUniqueId(),ally.id,"propor");domain.ally(visitor.getUniqueId(),home.id,"aceitar");
        assertTrue(explode(tnt(visitor),inside).blockList().isEmpty());
        domain.allyPermission(owner.getUniqueId(),ally.id,Domain.Access.BUILD,true);
        assertEquals(List.of(inside),explode(tnt(visitor),inside).blockList());
    }

    @Test void warOpensOnlyCollectiveClaimsWithExistingAsymmetry() {
        Domain domain=plugin.domain();
        Domain.Group attacker=domain.create(owner.getUniqueId(),"Roma","ROMA");
        Domain.Group defender=domain.create(visitor.getUniqueId(),"Vikings","VIK");
        Block defending=world.getBlockAt(24,64,8),personal=world.getBlockAt(40,64,8),solo=world.getBlockAt(56,64,8);
        domain.claim(visitor.getUniqueId(),chunk(defending),false);
        domain.claim(visitor.getUniqueId(),chunk(personal),true);
        domain.claim(outsider.getUniqueId(),chunk(solo),false);
        domain.declareWar(owner.getUniqueId(),defender.id,id->true);
        assertEquals(List.of(inside,defending),explode(tnt(owner),inside,defending,personal,solo).blockList());
        assertEquals(List.of(inside),explode(tnt(outsider),inside,defending,personal).blockList());
        assertTrue(domain.inWar(attacker.id));
    }

    @Test void tntWithoutPlayerSourceUsesApprovedAutomaticExceptionIncludingLostSource() {
        assertEquals(List.of(inside,wilderness),explode(tnt(null),inside,wilderness).blockList());
        Entity unknown=entity(Entity.class,EntityType.ZOMBIE,Map.of());
        assertEquals(List.of(inside),explode(tnt(unknown),inside).blockList());
        assertTrue(explode(unknown,inside).blockList().isEmpty(),"The exception is specific to TNT");
    }

    @Test void creeperUsesExplicitIgniterBeforeNaturalTargetAndNeverNearestPlayer() {
        // Null igniter is an actual API result, separate from unimplemented mock methods.
        Creeper natural=creeper(null,owner,false);
        assertEquals(List.of(inside),explode(natural,inside).blockList());
        assertTrue(explode(creeper(null,visitor,false),inside).blockList().isEmpty());
        assertEquals(List.of(inside),explode(creeper(owner,visitor,true),inside).blockList());
        assertTrue(explode(creeper(visitor,owner,true),inside).blockList().isEmpty());
        assertTrue(explode(creeper(null,owner,true),inside).blockList().isEmpty());
        assertTrue(explode(creeper(null,null,false),inside).blockList().isEmpty(),
                "All players are nearby, but proximity is not attribution");
    }

    Creeper creeper(Entity igniter, Entity target, boolean ignited) {
        Map<String,Object> fields=new java.util.HashMap<>();
        fields.put("getIgniter",igniter);fields.put("getTarget",target);fields.put("isIgnited",ignited);
        return entity(Creeper.class,EntityType.CREEPER,fields);
    }

    @Test void unattributedBlockExplosionsKeepProtection() {
        BlockExplodeEvent event=new BlockExplodeEvent(wilderness,wilderness.getState(),
                new ArrayList<>(List.of(inside,wilderness)),1.0F,ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());assertEquals(List.of(wilderness),event.blockList());
    }

    @Test void primingChecksDirectAndProjectileAuthorsButDoesNotBlockAutomaticTnt() {
        assertTrue(prime(TNTPrimeEvent.PrimeCause.PLAYER,visitor).isCancelled());
        assertFalse(prime(TNTPrimeEvent.PrimeCause.PLAYER,owner).isCancelled());
        Arrow arrow=entity(Arrow.class,EntityType.ARROW,Map.of("getShooter",visitor));
        assertTrue(prime(TNTPrimeEvent.PrimeCause.PROJECTILE,arrow).isCancelled());
        assertFalse(prime(TNTPrimeEvent.PrimeCause.REDSTONE,null).isCancelled());
        assertFalse(prime(TNTPrimeEvent.PrimeCause.DISPENSER,null).isCancelled());
        assertFalse(prime(TNTPrimeEvent.PrimeCause.EXPLOSION,tnt(null)).isCancelled());
    }

    TNTPrimeEvent prime(TNTPrimeEvent.PrimeCause cause, Entity source) {
        TNTPrimeEvent event=new TNTPrimeEvent(inside,cause,source,null);
        server.getPluginManager().callEvent(event);return event;
    }

    @Test void naturalIgnitionAndBurnWorkWhileDirectUnauthorizedIgnitionIsDenied() {
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.LAVA,null).isCancelled());
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.LIGHTNING,null).isCancelled());
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL,owner).isCancelled());
        assertTrue(ignite(BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL,visitor).isCancelled());
        Arrow arrow=entity(Arrow.class,EntityType.ARROW,Map.of("getShooter",visitor));
        assertTrue(ignite(BlockIgniteEvent.IgniteCause.ARROW,arrow).isCancelled());
        BlockBurnEvent burn=new BlockBurnEvent(inside,inside.getRelative(0,0,1));
        server.getPluginManager().callEvent(burn);assertFalse(burn.isCancelled());
    }

    BlockIgniteEvent ignite(BlockIgniteEvent.IgniteCause cause, Entity source) {
        BlockIgniteEvent event=new BlockIgniteEvent(inside,cause,source);
        server.getPluginManager().callEvent(event);return event;
    }

    @Test void dispenserIgnitionRespectsItsSourceBoundaryWithoutBlockingNaturalFire() {
        java.util.function.Function<Block,BlockProjectileSource> dispenserSource=block->
                (BlockProjectileSource)Proxy.newProxyInstance(BlockProjectileSource.class.getClassLoader(),
                        new Class<?>[]{BlockProjectileSource.class},(proxy,method,args)-> {
                            if (method.getName().equals("getBlock")) return block;
                            throw new UnsupportedOperationException("Unstubbed dispenser API: "+method.getName());
                        });
        Arrow internal=entity(Arrow.class,EntityType.ARROW,
                Map.of("getShooter",dispenserSource.apply(inside.getRelative(0,0,1))));
        Arrow external=entity(Arrow.class,EntityType.ARROW,
                Map.of("getShooter",dispenserSource.apply(wilderness)));
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.ARROW,internal).isCancelled());
        assertTrue(ignite(BlockIgniteEvent.IgniteCause.ARROW,external).isCancelled());
        Map<String,Object> unknownSource=new java.util.HashMap<>();unknownSource.put("getShooter",null);
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.ARROW,
                entity(Arrow.class,EntityType.ARROW,unknownSource)).isCancelled());
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.LAVA,null).isCancelled());
        assertFalse(ignite(BlockIgniteEvent.IgniteCause.LIGHTNING,null).isCancelled());
    }

    @Test void fireSpreadsInsideTerritoryAndExistingBoundaryGuardRemains() {
        assertFalse(spread(inside.getRelative(0,0,1),Material.FIRE).isCancelled());
        assertFalse(spread(inside.getRelative(0,0,1),Material.SOUL_FIRE).isCancelled());
        assertTrue(spread(wilderness,Material.FIRE).isCancelled());
    }

    BlockSpreadEvent spread(Block source, Material material) {
        var state=inside.getState();state.setType(material);
        BlockSpreadEvent event=new BlockSpreadEvent(inside,source,state);
        server.getPluginManager().callEvent(event);return event;
    }

    @Test void activeRaidBlocksEveryExplosionPrimingAndFireBeforeAndAfterBudgetIsSpent() throws Exception {
        plugin.raids().start(visitor);
        plugin.storageCheckpoint().get(5,TimeUnit.SECONDS);server.getScheduler().performOneTick();
        for (int count:List.of(0,30)) {
            for (int i=0;i<count;i++) plugin.raids().markBroken(visitor.getUniqueId(),inside);
            assertTrue(explode(tnt(null),inside).blockList().isEmpty());
            assertTrue(explode(tnt(owner),inside).blockList().isEmpty());
            assertTrue(prime(TNTPrimeEvent.PrimeCause.REDSTONE,null).isCancelled());
            assertTrue(prime(TNTPrimeEvent.PrimeCause.PLAYER,owner).isCancelled());
            assertTrue(ignite(BlockIgniteEvent.IgniteCause.LAVA,null).isCancelled());
            assertTrue(spread(inside.getRelative(0,0,1),Material.FIRE).isCancelled());
            BlockBurnEvent burn=new BlockBurnEvent(inside,null);
            server.getPluginManager().callEvent(burn);assertTrue(burn.isCancelled());
        }
        assertEquals(30,plugin.domain().s.raids.getFirst().blocks);
    }
}
