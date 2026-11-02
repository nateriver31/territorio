package dev.nateriver.territorio;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import static org.junit.jupiter.api.Assertions.*;

/** Event-level regressions; the simulated monitor does not replace live PvPManager validation. */
class GroupPvpRegressionTest {
    ServerMock server;
    TerritorioPlugin plugin;
    WorldMock world;
    PlayerMock leader, member, outsider;
    final AtomicBoolean override = new AtomicBoolean();
    final AtomicInteger combat = new AtomicInteger();

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("pvp");
        var external = MockBukkit.createMockPlugin("PvPManager");
        Listener observer = new Listener() {};
        // Match PvPManager 4.0.22's HIGHEST override and ignoreCancelled MONITOR.
        server.getPluginManager().registerEvent(EntityDamageByEntityEvent.class, observer,
            EventPriority.HIGHEST, (listener, event) -> {
                if (override.get()) ((EntityDamageByEntityEvent) event).setCancelled(false);
            }, external, false);
        server.getPluginManager().registerEvent(EntityDamageByEntityEvent.class, observer,
            EventPriority.MONITOR, (listener, event) -> combat.incrementAndGet(), external, true);
        server.getPluginManager().registerEvent(PotionSplashEvent.class, observer,
            EventPriority.MONITOR, (listener, event) -> {
                for (LivingEntity target : ((PotionSplashEvent) event).getAffectedEntities())
                    if (target.equals(member)) combat.incrementAndGet();
            }, external, true);
        server.getPluginManager().registerEvent(AreaEffectCloudApplyEvent.class, observer,
            EventPriority.MONITOR, (listener, event) -> {
                if (((AreaEffectCloudApplyEvent) event).getAffectedEntities().contains(member)) combat.incrementAndGet();
            }, external, true);
        plugin = MockBukkit.load(TerritorioPlugin.class);
        assertTrue(plugin.operational());
        leader = server.addPlayer("Lider");
        member = server.addPlayer("Membro");
        outsider = server.addPlayer("Outro");
        for (PlayerMock player : List.of(leader, member, outsider)) player.teleport(new Location(world, 8, 65, 8));
        plugin.domain().create(leader.getUniqueId(), "Imperio Romano", "ROMA");
        plugin.domain().invite(leader.getUniqueId(), member.getUniqueId());
        plugin.domain().accept(member.getUniqueId());
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private EntityDamageByEntityEvent attack(Entity source, Player target) {
        var event = new EntityDamageByEntityEvent(source, target,
            source instanceof Projectile ? EntityDamageEvent.DamageCause.PROJECTILE : EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(source).build(), 4.0);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test void teammatesCannotDamageEachOtherInWildernessOrAnyClaim() {
        assertTrue(attack(leader, member).isCancelled());
        assertTrue(attack(member, leader).isCancelled());
        plugin.domain().claim(leader.getUniqueId(), new Domain.Chunk(world.getUID(), 0, 0), false);
        assertTrue(attack(leader, member).isCancelled());
        member.teleport(new Location(world, 128, 65, 128));
        assertTrue(attack(leader, member).isCancelled());
        assertEquals(0, combat.get());
    }

    @Test void arrowsTridentsAndPlayerTntRespectTheSameGroupRule() {
        ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
        arrow.setShooter(leader);
        TridentMock trident = new TridentMock(server, UUID.randomUUID());
        trident.setShooter(leader);
        TNTPrimedMock tnt = new TNTPrimedMock(server, UUID.randomUUID()) {};
        tnt.setSource(leader);
        for (Entity source : List.of(arrow, trident, tnt)) assertTrue(attack(source, member).isCancelled());
        arrow.setShooter(outsider);
        assertFalse(attack(arrow, member).isCancelled());
        assertEquals(1, combat.get());
    }

    @Test void lateOverrideDoesNotCreateCombatForTeammates() {
        override.set(true);
        assertTrue(attack(leader, member).isCancelled());
        assertEquals(0, combat.get());
        assertFalse(attack(outsider, member).isCancelled());
        assertEquals(1, combat.get());
    }

    @Test void playerAttributedFireworksAndLightningAreBlockedButNaturalLightningIsAllowed() {
        FireworkMock firework = new FireworkMock(server, UUID.randomUUID());
        firework.setShooter(leader);
        assertTrue(attack(firework, member).isCancelled());
        LightningStrikeMock lightning = new LightningStrikeMock(server, UUID.randomUUID());
        lightning.setCausingPlayer(leader);
        assertTrue(attack(lightning, member).isCancelled());
        lightning.setCausingPlayer(null);
        assertFalse(attack(lightning, member).isCancelled());
    }

    @Test void alliesTrustAndSoloDoNotGrantFriendlyFireProtection() {
        Domain d = plugin.domain();
        var other = d.create(outsider.getUniqueId(), "Vikings", "VIK");
        var own = d.group(leader.getUniqueId());
        d.ally(leader.getUniqueId(), other.id, "propor");
        d.ally(outsider.getUniqueId(), own.id, "aceitar");
        assertFalse(attack(outsider, member).isCancelled());
        d.trust(leader.getUniqueId(), outsider.getUniqueId(), true);
        assertFalse(attack(outsider, leader).isCancelled());
        PlayerMock solo = server.addPlayer("Solo");
        assertFalse(attack(solo, leader).isCancelled());
        assertFalse(attack(leader, solo).isCancelled());
        assertFalse(attack(solo, solo).isCancelled());
        assertFalse(attack(leader, leader).isCancelled());
    }

    @Test void leaveRevokesOnlyTheFormerTeammatesDamageRestriction() {
        assertTrue(attack(leader, member).isCancelled());
        plugin.domain().leave(member.getUniqueId());
        assertFalse(attack(leader, member).isCancelled());
    }

    @Test void directAndProjectileCombustionAreBlockedOnlyForTeammates() {
        var direct = new EntityCombustByEntityEvent(leader, member, 8.0F);
        server.getPluginManager().callEvent(direct);
        assertTrue(direct.isCancelled());
        ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
        arrow.setShooter(leader);
        var flame = new EntityCombustByEntityEvent(arrow, member, 8.0F);
        server.getPluginManager().callEvent(flame);
        assertTrue(flame.isCancelled());
        var other = new EntityCombustByEntityEvent(outsider, member, 8.0F);
        server.getPluginManager().callEvent(other);
        assertFalse(other.isCancelled());
    }

    private SplashPotionMock potion(PotionEffectType effect) {
        SplashPotionMock potion = new SplashPotionMock(server, UUID.randomUUID());
        potion.setShooter(leader);
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(effect, 100, 0), true);
        item.setItemMeta(meta);
        potion.setItem(item);
        return potion;
    }

    @Test void harmfulSplashRemovesOnlyTeammateBeforeCombatMonitor() {
        var event = new PotionSplashEvent(potion(PotionEffectType.POISON), null, null, null,
            new HashMap<>(Map.of(member, 1.0, outsider, 0.75, leader, 1.0)));
        server.getPluginManager().callEvent(event);
        assertEquals(0.0, event.getIntensity(member));
        assertFalse(event.getAffectedEntities().contains(member));
        assertEquals(0.75, event.getIntensity(outsider));
        assertEquals(1.0, event.getIntensity(leader));
        assertFalse(event.isCancelled());
        assertEquals(0, combat.get());
    }

    @Test void beneficialSplashStillAffectsTeammates() {
        var event = new PotionSplashEvent(potion(PotionEffectType.REGENERATION), null, null, null, new HashMap<>(Map.of(member, 1.0)));
        server.getPluginManager().callEvent(event);
        assertEquals(1.0, event.getIntensity(member));
        assertFalse(event.isCancelled());
        assertEquals(0, combat.get());
    }

    @Test void harmfulCloudRemovesOnlyTeammateAndKeepsOtherTargets() {
        AreaEffectCloudMock cloud = new AreaEffectCloudMock(server, UUID.randomUUID());
        cloud.setSource(leader);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0), true);
        var event = new AreaEffectCloudApplyEvent(cloud, new ArrayList<>(List.of(member, outsider, leader)));
        server.getPluginManager().callEvent(event);
        assertEquals(List.of(outsider, leader), event.getAffectedEntities());
        assertFalse(event.isCancelled());
        assertEquals(0, combat.get());
    }

    @Test void beneficialCloudKeepsTeammatesAndUnattributedDamageStaysNormal() {
        AreaEffectCloudMock cloud = new AreaEffectCloudMock(server, UUID.randomUUID());
        cloud.setSource(leader);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0), true);
        var event = new AreaEffectCloudApplyEvent(cloud, new ArrayList<>(List.of(member)));
        server.getPluginManager().callEvent(event);
        assertEquals(List.of(member), event.getAffectedEntities());
        ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
        assertFalse(attack(arrow, member).isCancelled());
    }

    private EntityPotionEffectEvent effect(Entity source, PotionEffectType type) {
        var event=new EntityPotionEffectEvent(member,null,new PotionEffect(type,100,0),source,
            EntityPotionEffectEvent.Cause.POTION_SPLASH,EntityPotionEffectEvent.Action.ADDED,false);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test void mixedSplashKeepsBuffIntensityButRejectsOnlyHarmfulIndividualEffects() {
        SplashPotionMock mixed=potion(PotionEffectType.SLOWNESS);
        PotionMeta meta=mixed.getPotionMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,0),true);
        mixed.setPotionMeta(meta);
        var event=new PotionSplashEvent(mixed,null,null,null,new HashMap<>(Map.of(member,0.625,outsider,1.0)));
        server.getPluginManager().callEvent(event);
        assertEquals(0.625,event.getIntensity(member));
        assertEquals(1.0,event.getIntensity(outsider));
        assertEquals(0,combat.get());
        assertTrue(effect(mixed,PotionEffectType.SLOWNESS).isCancelled());
        assertFalse(effect(mixed,PotionEffectType.RESISTANCE).isCancelled());
    }

    @Test void turtleMasterCloudPreservesVanillaBuffApplicationWithoutTaggingTeammates() {
        AreaEffectCloudMock cloud=new AreaEffectCloudMock(server,UUID.randomUUID());
        cloud.setSource(leader);
        cloud.setBasePotionType(PotionType.TURTLE_MASTER);
        var event=new AreaEffectCloudApplyEvent(cloud,new ArrayList<>(List.of(member,outsider)));
        server.getPluginManager().callEvent(event);
        assertTrue(event.getAffectedEntities().containsAll(List.of(member,outsider)));
        assertEquals(2,event.getAffectedEntities().size());
        assertEquals(PotionType.TURTLE_MASTER,cloud.getBasePotionType());
        assertEquals(0,combat.get());
        assertTrue(effect(cloud,PotionEffectType.SLOWNESS).isCancelled());
        assertFalse(effect(cloud,PotionEffectType.RESISTANCE).isCancelled());
    }

    @Test void neutralEffectsCannotTagTeammatesEvenIfPvPManagerClassifiesThemAsHarmful() {
        var glowing=potion(PotionEffectType.GLOWING);
        var event=new PotionSplashEvent(glowing,null,null,null,new HashMap<>(Map.of(member,1.0)));
        server.getPluginManager().callEvent(event);
        assertEquals(1.0,event.getIntensity(member));
        assertEquals(0,combat.get());
        assertFalse(effect(glowing,PotionEffectType.GLOWING).isCancelled());
    }

    @Test void laterCancellationDoesNotRestorePotionOrCloudBuffRecipients() {
        Listener cancel=new Listener() {};
        server.getPluginManager().registerEvent(PotionSplashEvent.class,cancel,EventPriority.HIGHEST,
            (listener,event)->((PotionSplashEvent)event).setCancelled(true),plugin,false);
        server.getPluginManager().registerEvent(AreaEffectCloudApplyEvent.class,cancel,EventPriority.HIGHEST,
            (listener,event)->((AreaEffectCloudApplyEvent)event).setCancelled(true),plugin,false);
        var splash=new PotionSplashEvent(potion(PotionEffectType.REGENERATION),null,null,null,new HashMap<>(Map.of(member,1.0)));
        server.getPluginManager().callEvent(splash);
        assertTrue(splash.isCancelled());
        assertFalse(splash.getAffectedEntities().contains(member));
        var cloud=new AreaEffectCloudMock(server,UUID.randomUUID());
        cloud.setSource(leader);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION,100,0),true);
        var apply=new AreaEffectCloudApplyEvent(cloud,new ArrayList<>(List.of(member)));
        server.getPluginManager().callEvent(apply);
        assertTrue(apply.isCancelled());
        assertTrue(apply.getAffectedEntities().isEmpty());
        assertEquals(0,combat.get());
    }

    @Test void absentPvPManagerLeavesBuffTargetsUntouchedAndStillRejectsHarmfulEffects() {
        server.getPluginManager().disablePlugin(Objects.requireNonNull(server.getPluginManager().getPlugin("PvPManager")));
        SplashPotionMock mixed=potion(PotionEffectType.SLOWNESS);
        PotionMeta meta=mixed.getPotionMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,0),true);
        mixed.setPotionMeta(meta);
        var event=new PotionSplashEvent(mixed,null,null,null,new HashMap<>(Map.of(member,0.5)));
        server.getPluginManager().callEvent(event);
        assertEquals(0.5,event.getIntensity(member));
        assertTrue(effect(mixed,PotionEffectType.SLOWNESS).isCancelled());
        assertFalse(effect(mixed,PotionEffectType.RESISTANCE).isCancelled());
    }
}
