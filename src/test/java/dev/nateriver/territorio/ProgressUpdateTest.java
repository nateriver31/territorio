package dev.nateriver.territorio;

import java.util.*;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressUpdateTest extends UpdateTestBase {
    ProgressListener deterministic(double roll){return new ProgressListener(plugin,d,()->roll,bound->0);}
    void victimClaim(){d.claim(a.getUniqueId(),at(0),false);}
    @Test void productionListenerProcessesDeathOnlyOnceButCountsRepeatedLivesWithoutCooldown() throws Exception {
        groups();victimClaim();configure("intel-book.drop-chance",1.0);
        var event=death(b,a,DamageType.PLAYER_ATTACK);server.getPluginManager().callEvent(event);server.getPluginManager().callEvent(event);
        assertEquals(1,d.group(b.getUniqueId()).bonusEarned);assertEquals(1,event.getDrops().size());
        // A second event object for the same life is also ignored.
        var duplicate=death(b,a,DamageType.PLAYER_ATTACK);server.getPluginManager().callEvent(duplicate);assertTrue(duplicate.getDrops().isEmpty());
        server.getPluginManager().callEvent(respawn(a));var next=death(b,a,DamageType.PLAYER_ATTACK);server.getPluginManager().callEvent(next);
        assertEquals(2,d.group(b.getUniqueId()).bonusEarned);assertEquals(1,next.getDrops().size());
    }
    @Test void quarterChanceUsesPluginRandomAndLootingDoesNotChangeIt(){
        groups();victimClaim();
        for(int level=0;level<=3;level++) {
            ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);if(level>0)sword.addUnsafeEnchantment(Enchantment.LOOTING,level);
            b.getInventory().setItemInMainHand(sword);
            ProgressListener yes=deterministic(0.249999);var won=death(b,a,DamageType.PLAYER_ATTACK);yes.death(won);yes.death(won);assertEquals(1,won.getDrops().size());
            ProgressListener no=deterministic(0.25);var lost=death(b,a,DamageType.PLAYER_ATTACK);no.death(lost);assertTrue(lost.getDrops().isEmpty());
        }
    }
    @Test void zeroAndOneChanceBoundariesAndDisableAreReloadable() throws Exception {
        groups();victimClaim();configure("intel-book.drop-chance",0.0);
        var zero=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0).death(zero);assertTrue(zero.getDrops().isEmpty());
        configure("intel-book.drop-chance",1.0);var one=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0.999).death(one);assertEquals(1,one.getDrops().size());
        configure("intel-book.enabled",false);var off=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0).death(off);assertTrue(off.getDrops().isEmpty());assertEquals(Material.WRITTEN_BOOK,one.getDrops().getFirst().getType());
    }
    @Test void noBookForEnvironmentalMobAdminOrSameGroupDeathsEvenWithOldKillerCredit(){
        groups();join();victimClaim();a.setKiller(b);
        for(var type:List.of(DamageType.FALL,DamageType.LAVA,DamageType.GENERIC_KILL,DamageType.MOB_ATTACK)) {
            var event=death(null,a,type);deterministic(0).death(event);assertTrue(event.getDrops().isEmpty());
        }
        var command=death(b,a,DamageType.GENERIC_KILL);deterministic(0).death(command);assertTrue(command.getDrops().isEmpty());
        var friendly=death(member,a,DamageType.PLAYER_ATTACK);deterministic(0).death(friendly);assertTrue(friendly.getDrops().isEmpty());
        assertEquals(0,d.group(b.getUniqueId()).bonusEarned);assertEquals(0,d.group(a.getUniqueId()).bonusEarned);
    }
    @Test void soloKillerCanGetVictimGroupBookButNoBonusIsTransferred(){
        groups();victimClaim();var event=death(outsider,a,DamageType.PLAYER_ATTACK);deterministic(0).death(event);
        assertEquals(1,event.getDrops().size());assertEquals(0,d.group(a.getUniqueId()).bonusEarned);
        var soloVictim=death(b,outsider,DamageType.PLAYER_ATTACK);deterministic(0).death(soloVictim);assertTrue(soloVictim.getDrops().isEmpty());assertEquals(0,d.group(b.getUniqueId()).bonusEarned);
    }
    @Test void signedTradeableBookShowsOneChunkCenterVictimGroupAndDimension(){
        groups();victimClaim();d.claim(a.getUniqueId(),at(2),true);
        var event=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0).death(event);
        var item=event.getDrops().getFirst();assertEquals(Material.WRITTEN_BOOK,item.getType());
        BookMeta meta=(BookMeta)item.getItemMeta();assertTrue(meta.hasAuthor());assertTrue(meta.hasTitle());assertEquals(BookMeta.Generation.ORIGINAL,meta.getGeneration());
        String text=String.join("\n",meta.getPages());
        assertTrue(text.contains("ROMA"));assertTrue(text.contains("Imperio Romano"));assertTrue(text.contains("Romano"));
        assertTrue(text.contains("X: 8"));assertTrue(text.contains("Z: 8"));assertTrue(text.contains("update"));assertTrue(text.contains(world.getKey().toString()));
        assertFalse(text.contains("X: 40"),"Personal chunk is excluded by default");
    }
    @Test void eligibilityFlagsSelectNormalBonusOrPersonalWithoutRevealingAllClaims() throws Exception {
        groups();join();victimClaim();d.claim(member.getUniqueId(),at(2),true);earn(2);d.claimBonus(a.getUniqueId(),at(3));
        ProgressListener progress=deterministic(0);var g=d.group(a.getUniqueId());
        assertEquals(List.of(at(0),at(3)),progress.eligible(g,plugin.options()));
        configure("intel-book.include-normal-claims",false);assertEquals(List.of(at(3)),progress.eligible(g,plugin.options()));
        configure("intel-book.include-bonus-claims",false);assertTrue(progress.eligible(g,plugin.options()).isEmpty());
        configure("intel-book.include-personal-claims",true);assertEquals(List.of(at(2)),progress.eligible(g,plugin.options()));
    }
    @Test void noEligibleClaimMeansNoBookAndJustLostBonusCannotBeRevealed(){
        groups();earn(1);d.claimBonus(a.getUniqueId(),at(3));
        var event=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0).death(event);
        assertNull(d.claim(at(3)));assertTrue(event.getDrops().isEmpty());
    }
    @Test void cancelledDeathsDoNotConsumeLifeOrGenerateBonusOrBook(){
        groups();victimClaim();var event=death(b,a,DamageType.PLAYER_ATTACK);event.setCancelled(true);
        server.getPluginManager().callEvent(event);assertEquals(0,d.group(b.getUniqueId()).bonusEarned);assertTrue(event.getDrops().isEmpty());
    }
    @Test void validProjectileAuthorIsUsedWithoutDependingOnStaleKiller(){
        groups();victimClaim();var arrow=new org.mockbukkit.mockbukkit.entity.ArrowMock(server,UUID.randomUUID());arrow.setShooter(b);
        var source=DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(b).build();
        var event=new PlayerDeathEvent(a,source,new ArrayList<>(),0,net.kyori.adventure.text.Component.empty(),true);
        deterministic(0).death(event);assertEquals(1,d.group(b.getUniqueId()).bonusEarned);assertEquals(1,event.getDrops().size());
    }
    @Test void npcMetadataDoesNotIntroduceAnUnverifiedCombatLoggerIntegration(){
        groups();victimClaim();a.setMetadata("NPC",new org.bukkit.metadata.FixedMetadataValue(plugin,true));
        var event=death(b,a,DamageType.PLAYER_ATTACK);deterministic(0).death(event);
        assertTrue(event.getDrops().isEmpty());assertEquals(0,d.group(b.getUniqueId()).bonusEarned);
    }
}
