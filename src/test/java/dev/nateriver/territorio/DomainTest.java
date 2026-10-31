package dev.nateriver.territorio;

import static dev.nateriver.territorio.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DomainTest {
    final AtomicLong time = new AtomicLong(1_000_000);
    final Domain d = new Domain(time::get);
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(),
        e = UUID.randomUUID(), world = UUID.randomUUID();
    Chunk at(int x) { return new Chunk(world,x,0); }
    Group rome() { return d.create(a,"Império Romano","ROMA"); }
    void join(UUID leader,UUID player) { d.invite(leader,player);d.accept(player); }

    @Test void validatesCaseInsensitiveNamesTagsAndFormatting() {
        rome();
        assertThrows(IllegalArgumentException.class,()->d.create(b,"Outro Grupo","roma"));
        assertThrows(IllegalArgumentException.class,()->d.create(b,"IMPÉRIO ROMANO","VIK"));
        for(String tag:List.of("A","ABCDEF","§a","&a","A\n"))
            assertThrows(IllegalArgumentException.class,()->d.create(b,"Outro Grupo",tag));
        assertThrows(IllegalArgumentException.class,()->d.create(b," Nome","OK"));
        assertEquals(1,d.s.groups.size());
    }

    @Test void creatingAndJoiningMigrateSoloLandOnlyOnceAndKeepPersonalPrivate() {
        d.claim(a,at(0),false);d.claim(b,at(1),false);
        Group g=rome();join(a,b);
        assertEquals(2,d.count(Kind.GROUP,g.id));assertEquals(0,d.count(Kind.SOLO,a));
        assertEquals(18,d.capacity(g));assertEquals(g.id,d.claim(at(1)).owner);
        d.claim(b,at(2),true);
        assertEquals(Kind.PERSONAL,d.claim(at(2)).kind);
        assertFalse(d.canAccess(a,d.claim(at(2)),Access.CONTAINERS));
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(3),true));
        d.trust(b,a,true);assertTrue(d.canAccess(a,d.claim(at(2)),Access.CONTAINERS));
        d.trust(b,a,false);assertFalse(d.canAccess(a,d.claim(at(2)),Access.CONTAINERS));
    }

    @Test void soloLimitAndExistingClaimCannotBeOverwritten() {
        for(int i=0;i<9;i++)d.claim(a,at(i),false);
        assertThrows(IllegalArgumentException.class,()->d.claim(a,at(10),false));
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(0),false));
        assertEquals(a,d.claim(at(0)).owner);
    }

    @Test void allRanksAccessCollectiveButOnlyManagersManageIt() {
        Group g=rome();join(a,b);join(a,c);join(a,e);
        d.rank(a,b,Role.COLEADER);d.rank(b,c,Role.OFFICER);
        d.claim(b,at(0),false);
        for(UUID p:List.of(a,b,c,e))for(Access access:Access.values())
            assertTrue(d.canAccess(p,d.claim(at(0)),access));
        assertThrows(IllegalArgumentException.class,()->d.claim(c,at(1),false));
        assertThrows(IllegalArgumentException.class,()->d.claim(e,at(1),false));
        assertThrows(IllegalArgumentException.class,()->d.unclaim(c,at(0)));
        d.unclaim(b,at(0));assertNull(d.claim(at(0)));
        assertEquals(4,g.members.size());
    }

    @Test void singleColeaderAndRankAuthorityCannotBeBypassed() {
        rome();join(a,b);join(a,c);join(a,e);d.rank(a,b,Role.COLEADER);
        assertThrows(IllegalArgumentException.class,()->d.rank(a,c,Role.COLEADER));
        assertThrows(IllegalArgumentException.class,()->d.rank(b,c,Role.COLEADER));
        assertThrows(IllegalArgumentException.class,()->d.rank(b,a,Role.MEMBER));
        d.rank(b,c,Role.OFFICER);
        assertThrows(IllegalArgumentException.class,()->d.kick(b,c));
        assertThrows(IllegalArgumentException.class,()->d.rank(c,e,Role.OFFICER));
        d.rank(b,c,Role.MEMBER);d.kick(b,c);
        assertNull(d.group(c));assertFalse(d.s.joinCooldown.containsKey(c));
    }

    @Test void invitationExpiresAtFiveMinutesAndRefusalRemovesIt() {
        rome();d.invite(a,b);time.addAndGet(300_000);
        assertThrows(IllegalArgumentException.class,()->d.accept(b));
        assertNull(d.group(b));d.invite(a,b);d.refuse(b);
        assertFalse(d.s.invites.containsKey(b));
        assertThrows(IllegalArgumentException.class,()->d.accept(b));
    }

    @Test void leavingAppliesPersistentTwoHourDeadlineAndKickingDoesNot() {
        rome();join(a,b);d.leave(b);
        assertEquals(time.get()+7_200_000,d.s.joinCooldown.get(b));
        Domain restored=new Domain(time::get);restored.s=d.s;restored.reindex();
        assertThrows(IllegalArgumentException.class,()->restored.create(b,"Vikings","VIK"));
        assertThrows(IllegalArgumentException.class,()->restored.invite(a,b));
        time.addAndGet(7_200_000);restored.invite(a,b);restored.accept(b);
        restored.kick(a,b);assertEquals(time.get(),restored.s.joinCooldown.get(b));
        restored.create(b,"Vikings","VIK");assertNotNull(restored.group(b));
    }

    @Test void successionPrefersColeaderAndNeverChangesMerelyForOfflineState() {
        Group g=rome();join(a,b);join(a,c);d.rank(a,b,Role.COLEADER);d.rank(a,c,Role.OFFICER);
        assertEquals(a,g.leader);d.leave(a);assertEquals(b,g.leader);
        assertEquals(Role.LEADER,g.members.get(b).role);
    }

    @Test void successionUsesRankSeniorityThenUuidTieBreak() {
        Group g=rome();join(a,b);time.incrementAndGet();join(a,c);
        d.rank(a,c,Role.OFFICER);time.incrementAndGet();d.rank(a,b,Role.OFFICER);
        d.leave(a);assertEquals(c,g.leader);
        d.leave(c);assertEquals(b,g.leader);
        d.leave(b);assertTrue(d.s.groups.isEmpty());
        Domain equal=new Domain(time::get);Group tie=equal.create(a,"Grupo Empate","EMP");
        equal.invite(a,b);equal.accept(b);equal.invite(a,c);equal.accept(c);
        equal.leave(a);
        UUID expected=b.toString().compareTo(c.toString())<0?b:c;
        assertEquals(expected,tie.leader);
    }

    @Test void dissolutionReleasesOnlyCollectiveClaimsAndRetainsPersonal() {
        Group g=rome();join(a,b);d.claim(a,at(0),false);d.claim(b,at(1),true);
        d.dissolve(a);
        assertNull(d.s.groups.get(g.id));assertNull(d.claim(at(0)));
        assertEquals(b,d.claim(at(1)).owner);assertEquals(Kind.PERSONAL,d.claim(at(1)).kind);
        assertFalse(d.canAccess(a,d.claim(at(1)),Access.BUILD));
    }

    @Test void lossOfCapacityDoesNotRemoveClaimsButBlocksExpansion() {
        Group g=rome();join(a,b);for(int i=0;i<18;i++)d.claim(a,at(i),false);
        d.leave(b);assertEquals(18,d.count(Kind.GROUP,g.id));assertEquals(9,d.capacity(g));
        assertThrows(IllegalArgumentException.class,()->d.claim(a,at(30),false));
        for(int i=0;i<10;i++)d.unclaim(a,at(i));
        d.claim(a,at(30),false);assertEquals(9,d.count(Kind.GROUP,g.id));
    }

    @Test void victimCannotJoinOrCreateAndInviteRequiresExistingGroupAtAcceptance() {
        rome();d.invite(a,b);d.activeVictim=id->id.equals(b);
        assertThrows(IllegalArgumentException.class,()->d.accept(b));
        assertThrows(IllegalArgumentException.class,()->d.create(b,"Grupo Novo","NOV"));
        d.activeVictim=id->false;d.dissolve(a);
        assertThrows(IllegalArgumentException.class,()->d.accept(b));
    }

    @Test void warRequiresBothActualLeadersOnlineAndPreservesAsymmetry() {
        Group ga=rome(),gb=d.create(b,"Vikings","VIK");join(b,c);
        assertThrows(IllegalArgumentException.class,()->d.declareWar(a,gb.id,p->p.equals(a)));
        assertTrue(d.s.wars.isEmpty());
        d.declareWar(a,gb.id,p->true);
        Claim attacker=new Claim(Kind.GROUP,ga.id),defender=new Claim(Kind.GROUP,gb.id);
        for(Access access:Access.values()) {
            assertTrue(d.canAccess(e,attacker,access));assertFalse(d.canAccess(e,defender,access));
            assertTrue(d.canAccess(a,defender,access));assertTrue(d.canAccess(c,defender,access));
        }
        assertFalse(d.canAccess(e,new Claim(Kind.PERSONAL,a),Access.BUILD));
    }

    @Test void warBlocksExitKickDissolutionClaimAndUnclaimForAllRanks() {
        Group ga=rome();join(a,c);d.rank(a,c,Role.COLEADER);d.claim(a,at(0),false);
        Group gb=d.create(b,"Vikings","VIK");d.declareWar(a,gb.id,p->true);
        for(UUID p:List.of(a,c)) {
            assertThrows(IllegalArgumentException.class,()->d.leave(p));
            assertThrows(IllegalArgumentException.class,()->d.claim(p,at(1),false));
            assertThrows(IllegalArgumentException.class,()->d.unclaim(p,at(0)));
        }
        assertThrows(IllegalArgumentException.class,()->d.kick(a,c));
        assertThrows(IllegalArgumentException.class,()->d.dissolve(a));
        assertEquals(2,ga.members.size());assertNotNull(d.claim(at(0)));
    }

    @Test void peaceRequiresOpposingLeaderAndPendingOfferAndKeepsHistory() {
        rome();Group gb=d.create(b,"Vikings","VIK");join(a,c);
        War w=d.declareWar(a,gb.id,p->true);
        assertThrows(IllegalArgumentException.class,()->d.peace(b,d.group(a).id,"aceitar"));
        assertThrows(IllegalArgumentException.class,()->d.peace(c,gb.id,"propor"));
        d.peace(a,gb.id,"propor");assertEquals(d.group(a).id,w.peaceFrom);
        assertThrows(IllegalArgumentException.class,()->d.peace(a,gb.id,"aceitar"));
        d.peace(b,d.group(a).id,"recusar");assertNull(w.peaceFrom);assertTrue(d.inWar(gb.id));
        d.peace(a,gb.id,"propor");time.incrementAndGet();d.peace(b,d.group(a).id,"aceitar");
        assertFalse(d.inWar(gb.id));assertEquals("ACORDO_DE_PAZ",w.result);
        assertEquals(time.get(),w.ended);assertEquals(1,d.s.wars.size());
    }

    @Test void alliancePermissionsStartDisabledAreDirectionalAndNeverRestoreAfterWar() {
        Group ga=rome(),gb=d.create(b,"Vikings","VIK");
        d.ally(a,gb.id,"propor");d.ally(b,ga.id,"aceitar");
        Claim target=new Claim(Kind.GROUP,ga.id);
        for(Access access:Access.values())assertFalse(d.canAccess(b,target,access));
        d.allyPermission(a,gb.id,Access.MECHANISMS,true);
        assertTrue(d.canAccess(b,target,Access.MECHANISMS));assertFalse(d.canAccess(b,target,Access.CONTAINERS));
        assertFalse(d.canAccess(a,new Claim(Kind.GROUP,gb.id),Access.MECHANISMS));
        d.declareWar(a,gb.id,p->true);assertFalse(ga.allies.containsKey(gb.id));assertFalse(gb.allies.containsKey(ga.id));
        d.peace(a,gb.id,"propor");d.peace(b,ga.id,"aceitar");
        assertFalse(d.canAccess(b,target,Access.MECHANISMS));
        assertThrows(IllegalArgumentException.class,()->d.allyPermission(a,gb.id,Access.BUILD,true));
    }

    @Test void contributionsCannotBeReusedBySwitchingGroupsOrReturningToSolo() {
        Group old=rome();join(a,b);
        for(int i=0;i<18;i++)d.claim(a,at(i),false);
        d.kick(a,b); // No join cooldown, but nine credits remain backing old group's claims.
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(100),false));
        Group next=d.create(b,"Novo Grupo","NOV");
        assertEquals(0,d.capacity(next));
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(100),false));
        List<Chunk> backed=d.s.claims.entrySet().stream().filter(e->b.equals(e.getValue().contributor)).map(Map.Entry::getKey).toList();
        assertEquals(9,backed.size());d.unclaim(a,backed.getFirst());
        assertEquals(1,d.capacity(next));d.claim(b,at(100),false);
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(101),false));
        assertEquals(18,d.s.claims.size());assertEquals(17,d.count(Kind.GROUP,old.id));
    }
    @Test void leadershipTransferIsBlockedInWarAndWorksOutsideIt() {
        Group g=rome();join(a,c);d.rank(a,c,Role.LEADER);
        assertEquals(c,g.leader);assertEquals(Role.MEMBER,g.members.get(a).role);
        Group opponent=d.create(b,"Vikings","VIK");d.declareWar(c,opponent.id,p->true);
        assertThrows(IllegalArgumentException.class,()->d.rank(c,a,Role.LEADER));
        assertEquals(c,g.leader);
    }
    @Test void personalChunkRemainsPrivateAcrossLeavingDissolutionAndNewGroups() {
        rome();join(a,b);d.claim(b,at(20),true);d.leave(b);
        assertFalse(d.canAccess(a,d.claim(at(20)),Access.BUILD));
        time.addAndGet(7_200_000);d.create(b,"Vikings","VIK");
        assertThrows(IllegalArgumentException.class,()->d.claim(b,at(21),true));
        d.dissolve(a);assertNotNull(d.claim(at(20)));assertTrue(d.canAccess(b,d.claim(at(20)),Access.BUILD));
    }

    @Test void overCapacityGroupCanRegularizeByAddingMembersGraduallyWithoutExpansion() {
        Group g=rome();join(a,b);join(a,c);join(a,e);
        for(int i=0;i<36;i++)d.claim(a,at(i),false);
        d.kick(a,b);d.kick(a,c);d.kick(a,e);
        UUID newcomer=UUID.randomUUID();join(a,newcomer);
        assertEquals(18,d.capacity(g));assertEquals(36,d.count(Kind.GROUP,g.id));
        assertNotNull(d.group(newcomer));assertThrows(IllegalArgumentException.class,()->d.claim(a,at(100),false));
    }
}
