package dev.nateriver.territorio;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.nateriver.territorio.Domain.*;
import static org.junit.jupiter.api.Assertions.*;

class BonusDomainTest {
    final AtomicLong time=new AtomicLong(1_000_000);
    final Domain d=new Domain(time::get);
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),member=UUID.randomUUID(),world=UUID.randomUUID();
    final List<Domain.Change> changes=new ArrayList<>();
    Group one,two;
    BonusDomainTest(){d.onEvent=changes::add;one=d.create(a,"Roma","ROMA");two=d.create(b,"Vikings","VIK");changes.clear();}
    Chunk at(int x){return new Chunk(world,x,-2);}
    void join(){d.invite(a,member);d.accept(member);}
    void earn(int n){for(int i=0;i<n;i++)d.pvpKill(a,b);}
    void claim(int n){for(int i=0;i<n;i++)d.claimBonus(a,at(i));}
    List<Chunk> lost(){return changes.stream().filter(c->c.category().equals("BONUS_LOSS")).map(Domain.Change::lost).toList();}

    @Test void newAndExistingMembershipAddsOnlyCapacityNotBalance(){
        assertEquals(0,one.bonusEarned);assertEquals(6,d.bonusCapacity(one));join();
        assertEquals(12,d.bonusCapacity(one));assertEquals(0,one.bonusEarned);
        assertThrows(IllegalArgumentException.class,()->d.claimBonus(a,at(0)));
    }
    @Test void independentKillOperationsAllowWinAgainstZeroAndRepeatedKillsWithoutCooldown(){
        earn(9);assertEquals(6,one.bonusEarned);assertEquals(0,two.bonusEarned);
        assertEquals(9,changes.stream().filter(c->c.category().equals("PVP")).count());
        StateStore.validate(d.s);
    }
    @Test void cappedWinnerStillMakesLoserLoseAndZeroLoserDoesNotBecomeNegative(){
        earn(6);two.bonusEarned=2;d.pvpKill(a,b);
        assertEquals(6,one.bonusEarned);assertEquals(1,two.bonusEarned);
        d.pvpKill(a,b);d.pvpKill(a,b);assertEquals(0,two.bonusEarned);StateStore.validate(d.s);
    }
    @Test void sameGroupAndSoloKillsDoNotTransferBonus(){
        join();UUID solo=UUID.randomUUID();
        d.pvpKill(a,member);d.pvpKill(a,solo);d.pvpKill(solo,b);
        assertEquals(0,one.bonusEarned);assertEquals(0,two.bonusEarned);assertTrue(changes.stream().noneMatch(c->c.category().equals("PVP")));
    }
    @Test void lifoRemovesOnlyBonusProtectionAndKeepsNormalAndPersonalClaims(){
        earn(3);claim(3);d.claim(a,at(20),false);d.claim(a,at(21),true);changes.clear();
        d.pvpKill(b,a);assertEquals(List.of(at(2)),lost());assertNull(d.claim(at(2)));
        assertNotNull(d.claim(at(0)));assertNotNull(d.claim(at(1)));
        assertEquals(Kind.GROUP,d.claim(at(20)).kind);assertEquals(Kind.PERSONAL,d.claim(at(21)).kind);
        assertEquals(2,one.bonusEarned);StateStore.validate(d.s);
    }
    @Test void unusedEarnedBalanceAbsorbsLossWithoutReleasingChunksOrLossNotice(){
        earn(6);claim(3);changes.clear();d.pvpKill(b,a);
        assertEquals(5,one.bonusEarned);assertEquals(3,d.count(Kind.BONUS,one.id));assertTrue(lost().isEmpty());
    }
    @Test void bonusLandNeverConsumesNormalContributionOrPersonalLimit(){
        earn(6);claim(6);for(int i=10;i<19;i++)d.claim(a,at(i),false);d.claim(a,at(30),true);
        assertEquals(9,d.capacity(one));assertEquals(9,d.count(Kind.GROUP,one.id));
        assertThrows(IllegalArgumentException.class,()->d.claim(a,at(40),false));
        assertThrows(IllegalArgumentException.class,()->d.claim(a,at(41),true));
        assertEquals(6,d.count(Kind.BONUS,one.id));StateStore.validate(d.s);
    }
    @Test void leaveWithSixEarnedNeedsNoLossAndDoesNotTouchNormalExcess(){
        join();earn(6);claim(6);for(int i=10;i<28;i++)d.claim(a,at(i),false);changes.clear();
        d.leave(member);assertEquals(6,one.bonusEarned);assertEquals(6,d.bonusCapacity(one));
        assertEquals(18,d.count(Kind.GROUP,one.id));assertEquals(9,d.capacity(one));assertTrue(lost().isEmpty());
    }
    @Test void leavingTwelveToSixReleasesNewestSixAndReturnDoesNotRestoreThem(){
        join();earn(12);claim(12);changes.clear();d.leave(member);
        assertEquals(List.of(at(11),at(10),at(9),at(8),at(7),at(6)),lost());
        assertEquals(6,one.bonusEarned);assertEquals(6,d.count(Kind.BONUS,one.id));
        assertTrue(changes.stream().filter(c->c.category().equals("BONUS_LOSS")).allMatch(c->c.recipients().equals(List.of(a))));
        time.addAndGet(7_200_000);d.invite(a,member);d.accept(member);
        assertEquals(12,d.bonusCapacity(one));assertEquals(6,one.bonusEarned);assertEquals(6,d.count(Kind.BONUS,one.id));StateStore.validate(d.s);
    }
    @Test void kickClampsBalanceWithoutUnnecessaryLossAndNeverTransportsIt(){
        join();earn(12);claim(3);changes.clear();d.kick(a,member);
        assertEquals(6,one.bonusEarned);assertEquals(3,d.count(Kind.BONUS,one.id));assertTrue(lost().isEmpty());
        d.invite(b,member);d.accept(member);assertEquals(0,two.bonusEarned);assertEquals(12,d.bonusCapacity(two));
    }
    @Test void unclaimDoesNotSpendEarnedBalanceAndReclaimHasNewChronologicalOrder(){
        earn(3);claim(3);d.unclaim(a,at(1));d.claimBonus(a,at(4));
        assertEquals(3,one.bonusEarned);assertEquals(4,d.claim(at(4)).bonusOrder);
        changes.clear();d.pvpKill(b,a);assertEquals(List.of(at(4)),lost());
    }
    @Test void bonusPermissionsFollowCollectiveRulesIncludingWarButNotPersonalTrust(){
        earn(2);claim(1);join();assertTrue(d.canAccess(member,d.claim(at(0)),Access.BUILD));
        d.trust(a,b,true);assertFalse(d.canAccess(b,d.claim(at(0)),Access.BUILD));
        d.ally(a,two.id,"propor");d.ally(b,one.id,"aceitar");d.allyPermission(a,two.id,Access.CONTAINERS,true);
        assertTrue(d.canAccess(b,d.claim(at(0)),Access.CONTAINERS));assertFalse(d.canAccess(b,d.claim(at(0)),Access.BUILD));
        d.declareWar(a,two.id,id->true);assertTrue(d.canAccess(UUID.randomUUID(),d.claim(at(0)),Access.BUILD));
        assertThrows(IllegalArgumentException.class,()->d.claimBonus(a,at(1)));
        assertThrows(IllegalArgumentException.class,()->d.unclaim(a,at(0)));
    }
    @Test void disabledOptionsDoNotEraseBalancesOrClaimsAndReenableDoesNotReplayKills(){
        earn(3);claim(3);d.bonusEnabled=false;d.pvpKill(b,a);
        assertEquals(3,one.bonusEarned);assertEquals(3,d.count(Kind.BONUS,one.id));
        assertThrows(IllegalArgumentException.class,()->d.claimBonus(a,at(4)));
        d.bonusEnabled=true;d.bonusPvpEnabled=false;d.pvpKill(b,a);assertEquals(3,one.bonusEarned);
        d.bonusPvpEnabled=true;assertEquals(3,one.bonusEarned);d.pvpKill(b,a);assertEquals(2,one.bonusEarned);
    }
    @Test void adminDeletionClosesWarsAndReferencesButPreservesPersonalAndUnrelatedProgress(){
        earn(2);claim(2);join();d.claim(member,at(20),true);d.claim(a,at(21),false);d.claim(b,at(22),false);
        War war=d.declareWar(a,two.id,id->true);d.peace(a,two.id,"propor");
        UUID invited=UUID.randomUUID();d.invite(a,invited);d.s.allianceInvites.put(one.id,new AllianceInvite(two.id,one.id,d.now()+1000));
        d.adminDelete(one.id,"CONSOLE");assertFalse(d.s.groups.containsKey(one.id));assertNull(d.group(member));
        assertNull(d.claim(at(0)));assertNull(d.claim(at(21)));assertNotNull(d.claim(at(20)));assertNotNull(d.claim(at(22)));
        assertNotEquals(0,war.ended);assertNull(war.peaceFrom);assertFalse(d.s.invites.containsKey(invited));assertTrue(d.s.allianceInvites.isEmpty());
        assertEquals("[ROMA] Roma",war.attackerLabel);StateStore.validate(d.s);
    }
}
