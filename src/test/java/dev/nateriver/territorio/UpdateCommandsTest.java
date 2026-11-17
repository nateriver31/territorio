package dev.nateriver.territorio;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCommandsTest extends UpdateTestBase {
    @Test void claimsShowsGroupNormalEarnedUsedPersonalAndCurrentOwnerWithoutSoloLine(){
        groups();join();earn(3);d.claim(a.getUniqueId(),at(0),false);d.claimBonus(a.getUniqueId(),at(2));
        a.teleport(new Location(world,40,65,8));clearMessages();a.performCommand("t claims");String text=String.join("\n",messages(a));
        assertTrue(text.contains("Grupo: ROMA"));assertTrue(text.contains("Claims normais: 1/18"));assertTrue(text.contains("Claims Bônus conquistados: 3/12"));
        assertTrue(text.contains("Claims Bônus utilizados: 1/3"));assertTrue(text.contains("Claims pessoais: 0/1"));
        assertTrue(text.contains("Chunk atual: Claim Bônus"));assertTrue(text.contains("Imperio Romano"));assertFalse(text.contains("Seus claims solo"));
        outsider.performCommand("t claims");assertTrue(String.join("\n",messages(outsider)).contains("Seus claims solo: 0/9"));
    }
    @Test void claimBonusAliasUsesExistingManagerPermissionAndDoesNotConvertNormalClaims(){
        groups();join();earn(2);a.performCommand("t claim bonus");assertEquals(Domain.Kind.BONUS,d.claim(at(2)).kind);
        member.teleport(new Location(world,56,65,8));member.performCommand("t claim bonus");assertNull(d.claim(at(3)));
        a.teleport(new Location(world,72,65,8));a.performCommand("claim");assertEquals(Domain.Kind.GROUP,d.claim(at(4)).kind);
        a.performCommand("t claim bonus");assertEquals(Domain.Kind.GROUP,d.claim(at(4)).kind);
    }
    @Test void zombieMayAttackVillagerButPlayerProjectileAndBlockProtectionRemain(){
        d.claim(a.getUniqueId(),at(0),false);
        var zombie=new ZombieMock(server,UUID.randomUUID());zombie.teleport(new Location(world,8,65,8));
        var villager=new VillagerMock(server,UUID.randomUUID());villager.teleport(new Location(world,8,65,8));
        var damage=new EntityDamageByEntityEvent(zombie,villager,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(zombie).build(),4);
        server.getPluginManager().callEvent(damage);assertFalse(damage.isCancelled());
        var playerHit=new EntityDamageByEntityEvent(b,villager,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(b).build(),4);
        server.getPluginManager().callEvent(playerHit);assertTrue(playerHit.isCancelled());
        var arrow=new ArrowMock(server,UUID.randomUUID());arrow.setShooter(b);
        var shot=new EntityDamageByEntityEvent(arrow,villager,org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE,DamageSource.builder(DamageType.ARROW).withCausingEntity(b).build(),4);
        server.getPluginManager().callEvent(shot);assertTrue(shot.isCancelled());
        var block=new org.bukkit.event.entity.EntityChangeBlockEvent(zombie,world.getBlockAt(8,64,8),Material.AIR.createBlockData());
        server.getPluginManager().callEvent(block);assertTrue(block.isCancelled());
    }
    @Test void bonusEntryMessagesArePrivateAndDistinguishLatestFromEarlierChunks(){
        groups();join();earn(2);d.claimBonus(a.getUniqueId(),at(0));d.claimBonus(a.getUniqueId(),at(1));clearMessages();
        member.teleport(new Location(world,24,65,8));server.getScheduler().performTicks(2);
        String latest=String.join("\n",messages(member));assertTrue(latest.contains("mais recente (2º de 2)"));assertTrue(latest.contains("primeiro removido"));
        assertTrue(messages(a).isEmpty());assertTrue(messages(b).isEmpty());
        member.teleport(new Location(world,25,65,8));server.getScheduler().performTicks(2);assertTrue(messages(member).isEmpty());
        member.teleport(new Location(world,8,65,8));server.getScheduler().performTicks(2);assertTrue(String.join("\n",messages(member)).contains("1º de 2"));
        outsider.teleport(new Location(world,24,65,8));server.getScheduler().performTicks(2);String visit=String.join("\n",messages(outsider));
        assertTrue(visit.contains("Claim Bônus do grupo"));assertTrue(visit.contains("ROMA"));assertFalse(visit.contains("2º"));assertFalse(visit.contains("primeiro removido"));
    }
    @Test void lossNoticeGoesOnlyToAffectedOnlineMembersAndNotForBalanceOnly(){
        groups();join();earn(2);d.claimBonus(a.getUniqueId(),at(0));d.claimBonus(a.getUniqueId(),at(1));clearMessages();d.pvpKill(b.getUniqueId(),a.getUniqueId());
        assertTrue(String.join("\n",messages(a)).contains("Seu grupo perdeu um Claim Bônus"));assertTrue(String.join("\n",messages(member)).contains("Chunk desprotegido"));
        assertTrue(messages(b).isEmpty());assertTrue(messages(outsider).isEmpty());
        d.unclaim(a.getUniqueId(),at(0));clearMessages();d.pvpKill(b.getUniqueId(),a.getUniqueId());
        assertTrue(messages(a).isEmpty());assertTrue(messages(member).isEmpty());
    }
    @Test void invalidReloadPreservesEveryRunningOptionAndAllLand() throws Exception {
        groups();earn(6);d.claimBonus(a.getUniqueId(),at(0));var old=plugin.options();
        var file=plugin.getDataFolder().toPath().resolve("config.yml").toFile();var config=YamlConfiguration.loadConfiguration(file);
        config.set("claim-bonus.max-per-member",2);config.set("intel-book.enabled",false);config.save(file);
        assertThrows(IllegalArgumentException.class,plugin::reloadSafely);assertEquals(old,plugin.options());assertEquals(6,d.s.bonusMaxPerMember);
        assertEquals(6,d.group(a.getUniqueId()).bonusEarned);assertNotNull(d.claim(at(0)));
        config.set("claim-bonus.max-per-member",6);config.set("intel-book.drop-chance",1.1);config.save(file);assertThrows(IllegalArgumentException.class,plugin::reloadSafely);assertEquals(old,plugin.options());
        Files.writeString(file.toPath(),"claim-bonus: [invalid: yaml");assertThrows(IllegalArgumentException.class,plugin::reloadSafely);assertEquals(old,plugin.options());
    }
    @Test void disablingAndReenablingViaOldReloadCommandDoesNotRemoveLandOrReplayKills() throws Exception {
        groups();earn(2);d.claimBonus(a.getUniqueId(),at(0));a.addAttachment(plugin,"territorio.admin.reload",true);
        var file=plugin.getDataFolder().toPath().resolve("config.yml").toFile();var config=YamlConfiguration.loadConfiguration(file);
        config.set("claim-bonus.enabled",false);config.save(file);a.performCommand("t reload");assertFalse(d.bonusEnabled);
        d.pvpKill(b.getUniqueId(),a.getUniqueId());assertEquals(2,d.group(a.getUniqueId()).bonusEarned);assertNotNull(d.claim(at(0)));
        config.set("claim-bonus.enabled",true);config.set("claim-bonus.pvp.enabled",false);config.save(file);a.performCommand("t admin reload");
        assertTrue(d.bonusEnabled);assertFalse(d.bonusPvpEnabled);assertEquals(2,d.group(a.getUniqueId()).bonusEarned);
    }
    @Test void adminDeletionRequiresPermissionExecutorBoundConfirmationAndRevalidation(){
        groups();a.performCommand("t admin deletar VIK");a.performCommand("t admin deletar VIK confirmar");assertNotNull(d.group(b.getUniqueId()));
        a.addAttachment(plugin,"territorio.admin.deletar",true);b.addAttachment(plugin,"territorio.admin.deletar",true);
        a.performCommand("t admin deletar VIK");b.performCommand("t admin deletar VIK confirmar");assertNotNull(d.group(b.getUniqueId()));
        d.claim(b.getUniqueId(),at(0),false);a.performCommand("t admin deletar VIK confirmar");assertNotNull(d.group(b.getUniqueId()));
        a.performCommand("t admin deletar VIK");a.performCommand("t admin deletar VIK confirmar");assertNull(d.group(b.getUniqueId()));assertNull(d.claim(at(0)));StateStore.validate(d.s);
    }
    @Test void deletionConfirmationExpiresAtSixtySecondsAndConsoleCanConfirmOwnRequest(){
        groups();AtomicLong now=new AtomicLong(1_000);
        Commands commands=new Commands(plugin,d,plugin.raids(),now::get);a.addAttachment(plugin,"territorio.admin.deletar",true);
        commands.onCommand(a,plugin.getCommand("territorio"),"t",new String[]{"admin","deletar","VIK"});now.addAndGet(60_000);
        commands.onCommand(a,plugin.getCommand("territorio"),"t",new String[]{"admin","deletar","VIK","confirmar"});assertNotNull(d.group(b.getUniqueId()));
        var console=server.getConsoleSender();console.addAttachment(plugin,"territorio.admin.deletar",true);
        commands.onCommand(console,plugin.getCommand("territorio"),"t",new String[]{"admin","deletar","VIK"});
        commands.onCommand(a,plugin.getCommand("territorio"),"t",new String[]{"admin","deletar","VIK","confirmar"});assertNotNull(d.group(b.getUniqueId()));
        commands.onCommand(console,plugin.getCommand("territorio"),"t",new String[]{"admin","deletar","VIK","confirmar"});assertNull(d.group(b.getUniqueId()));commands.close();
    }
    @Test void newAdminSuggestionsRespectPermissionsAndOldCommandsRemainAvailable(){
        groups();var command=plugin.getCommand("territorio");assertFalse(command.tabComplete(a,"t",new String[]{"admin",""}).contains("deletar"));
        a.addAttachment(plugin,"territorio.admin.deletar",true);a.addAttachment(plugin,"territorio.admin.discord",true);
        assertTrue(command.tabComplete(a,"t",new String[]{"admin",""}).containsAll(List.of("deletar","discord")));
        assertEquals(List.of("teste"),command.tabComplete(a,"t",new String[]{"admin","discord",""}));
        assertTrue(command.tabComplete(a,"t",new String[]{"claim",""}).containsAll(List.of("bonus","pessoal")));
        assertFalse(command.tabComplete(outsider,"t",new String[]{"claim",""}).contains("bonus"));
    }
    @Test void failedPersistenceEntersMaintenanceAndKeepsOriginalFile() throws Exception {
        groups();saved();Path file=plugin.getDataFolder().toPath().resolve("state.json");String original=Files.readString(file);
        Files.createDirectory(file.resolveSibling("state.json.tmp"));d.claim(a.getUniqueId(),at(0),false);
        assertThrows(Exception.class,()->plugin.storageCheckpoint().get(5,java.util.concurrent.TimeUnit.SECONDS));assertFalse(plugin.operational());
        assertEquals(original,Files.readString(file));
        var event=new org.bukkit.event.block.BlockBreakEvent(world.getBlockAt(72,64,8),b);server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());
    }
    @Test void bonusLandUsesExistingProtectionAndAsymmetricWarPermissions(){
        groups();earn(3);d.pvpKill(b.getUniqueId(),a.getUniqueId());
        d.claimBonus(a.getUniqueId(),at(0));d.claimBonus(a.getUniqueId(),at(1));d.claimBonus(b.getUniqueId(),at(4));
        var block=world.getBlockAt(8,64,8);block.setType(Material.STONE);
        var outsiderBreak=new org.bukkit.event.block.BlockBreakEvent(block,outsider);server.getPluginManager().callEvent(outsiderBreak);assertTrue(outsiderBreak.isCancelled());
        var ownerBreak=new org.bukkit.event.block.BlockBreakEvent(block,a);server.getPluginManager().callEvent(ownerBreak);assertFalse(ownerBreak.isCancelled());
        d.declareWar(b.getUniqueId(),d.group(a.getUniqueId()).id,id->true);
        var attackerBreak=new org.bukkit.event.block.BlockBreakEvent(block,b);server.getPluginManager().callEvent(attackerBreak);assertFalse(attackerBreak.isCancelled());
        var thirdParty=new org.bukkit.event.block.BlockBreakEvent(block,outsider);server.getPluginManager().callEvent(thirdParty);assertTrue(thirdParty.isCancelled());
        var exposed=new org.bukkit.event.block.BlockBreakEvent(world.getBlockAt(72,64,8),outsider);server.getPluginManager().callEvent(exposed);assertFalse(exposed.isCancelled());
        StateStore.validate(d.s);
    }
}
