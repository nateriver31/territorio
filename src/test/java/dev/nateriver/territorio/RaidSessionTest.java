package dev.nateriver.territorio;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaidSessionTest {
    private RaidService.Session session() {
        return new RaidService.Session(UUID.randomUUID(), UUID.randomUUID(), 1_000_000,
            300_000, 86_400_000, 60_000, 30, new Domain.RaidRecord());
    }

    @Test void originalDeadlineNeverMoves() {
        var raid = session();
        assertTrue(raid.alive(raid.started + 299_999));
        assertFalse(raid.alive(raid.started + 300_000));
        assertFalse(raid.ownerReconnected(raid.started + 300_000));
        assertEquals(raid.started + 300_000, raid.deadline);
    }

    @Test void sharedBlockBudgetRejectsOneOrTwoBlockOverflow() {
        var raid = session();
        raid.blocks = 29;
        assertTrue(raid.canSpend(1, raid.started));
        assertFalse(raid.canSpend(2, raid.started));
        raid.blocks = 30;
        assertFalse(raid.canSpend(1, raid.started));
        // Reaching the budget does not end access to containers.
        assertTrue(raid.alive(raid.started + 1000));
    }

    @Test void offlineGraceDoesNotExtendOriginalDeadline() {
        var raid = session();
        raid.ownerDisconnected(raid.started + 280_000);
        assertEquals(raid.deadline, raid.offlineUntil);
        assertTrue(raid.alive(raid.deadline - 1));
        assertFalse(raid.alive(raid.deadline));
    }

    @Test void reconnectionInsideWindowClearsOnlyOfflineDeadline() {
        var raid = session();
        raid.ownerDisconnected(raid.started + 10_000);
        assertEquals(raid.started + 70_000, raid.offlineUntil);
        assertTrue(raid.ownerReconnected(raid.started + 69_999));
        assertEquals(0, raid.offlineUntil);
        assertTrue(raid.alive(raid.started + 100_000));
        assertFalse(raid.alive(raid.deadline));
    }

    @Test void reconnectionAtOrAfterGraceCannotReviveSessionBetweenTicks() {
        var raid = session();
        raid.ownerDisconnected(raid.started + 10_000);
        assertFalse(raid.ownerReconnected(raid.started + 70_000));
        assertFalse(raid.alive(raid.started + 70_000));
        raid.ownerDisconnected(raid.started + 71_000);
        assertEquals(raid.started + 70_000, raid.offlineUntil);
    }

    @Test void countdownAnnouncesEachLandmarkExactlyOnce() {
        var raid = session();
        Set<Integer> notified = new HashSet<>();
        int count = 0;
        for (int second = 0; second <= 300; second++) {
            List<Integer> notices = raid.notifications(raid.started + second * 1000L);
            for (int notice : notices) {
                assertEquals(notice, raid.remainingSeconds(raid.started + second * 1000L));
                assertTrue(notified.add(notice));
                count++;
            }
        }
        assertEquals(Set.of(240, 180, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), notified);
        assertEquals(15, count);
    }

    @Test void earlyEndingCancelsAllRemainingNoticesAndPermissions() {
        var raid = session();
        assertEquals(List.of(240), raid.notifications(raid.started + 60_000));
        raid.ended = true;
        assertFalse(raid.alive(raid.started + 61_000));
        assertFalse(raid.canSpend(1, raid.started + 61_000));
        assertTrue(raid.notifications(raid.started + 290_000).isEmpty());
        assertFalse(raid.ownerReconnected(raid.started + 62_000));
    }

    @Test void extendedProtectionIsAnchoredToStartRatherThanAccumulated() {
        var raid = session();
        long normalProtection = raid.started + 43_200_000;
        long defenseProtection = Math.max(normalProtection, raid.started + raid.defenseProtection);
        assertEquals(raid.started + 86_400_000, defenseProtection);
        assertEquals(defenseProtection, Math.max(defenseProtection, raid.started + raid.defenseProtection));
    }
}
