package dev.nateriver.territorio;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ActivityLogTest {
    @TempDir Path folder;
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-17T18:00:00Z");
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    @Test void dailyFilesPersistAcrossRestartUseLocalTimeAndKeepRecordsOrdered() throws Exception {
        MutableClock clock=new MutableClock();List<String> warnings=new ArrayList<>();
        try(ActivityLog log=new ActivityLog(folder,30,warnings::add,clock)) {
            log.record("PVP","primeiro uuid=123");log.record("BONUS","segundo");log.flush();
            assertEquals(List.of("[15:00:00] [PVP] primeiro uuid=123","[15:00:00] [BONUS] segundo"),Files.readAllLines(folder.resolve("logs/territorio-2026-11-17.log")));
            clock.now=Instant.parse("2026-11-18T18:00:00Z");log.record("INTEL","terceiro");log.flush();
            assertTrue(Files.exists(folder.resolve("logs/territorio-2026-11-18.log")));
        }
        try(ActivityLog restarted=new ActivityLog(folder,30,warnings::add,clock)) {restarted.record("ADMIN","reinício");restarted.flush();}
        assertEquals(2,Files.readAllLines(folder.resolve("logs/territorio-2026-11-18.log")).size());assertTrue(warnings.isEmpty());
    }
    @Test void retentionDeletesOnlyOldOwnDailyLogsAndNeverStateBackupsAuditOrOtherFiles() throws Exception {
        Path logs=Files.createDirectory(folder.resolve("logs"));
        Files.writeString(logs.resolve("territorio-2026-11-14.log"),"antigo");
        Files.writeString(logs.resolve("territorio-2026-11-16.log"),"recente");
        Files.writeString(logs.resolve("outro-2026-11-14.log"),"externo");
        for(String file:List.of("state.json","state.json.bak","state.json.pre-v3.bak","audit.jsonl"))Files.writeString(folder.resolve(file),"preservar");
        try(ActivityLog log=new ActivityLog(folder,2,e->fail(e),new MutableClock())) {log.record("GROUP","atual");log.flush();}
        assertFalse(Files.exists(logs.resolve("territorio-2026-11-14.log")));assertTrue(Files.exists(logs.resolve("territorio-2026-11-16.log")));
        assertEquals("externo",Files.readString(logs.resolve("outro-2026-11-14.log")));
        for(String file:List.of("state.json","state.json.bak","state.json.pre-v3.bak","audit.jsonl"))assertEquals("preservar",Files.readString(folder.resolve(file)));
    }
    @Test void ioFailuresAreReportedWithoutTouchingTerritoryDataOrThrowingOnMainCaller() throws Exception {
        Files.writeString(folder.resolve("logs"),"not a folder");Files.writeString(folder.resolve("state.json"),"territory data");
        List<String> warnings=new java.util.concurrent.CopyOnWriteArrayList<>();
        try(ActivityLog log=new ActivityLog(folder,30,warnings::add)) {assertDoesNotThrow(()->log.record("PVP","tentativa"));log.flush();assertTrue(log.failed());}
        assertFalse(warnings.isEmpty());assertEquals("territory data",Files.readString(folder.resolve("state.json")));
    }
    @Test void embeddedNewlinesCannotForgeExtraLogRecords() throws Exception {
        try(ActivityLog log=new ActivityLog(folder,30,e->fail(e),new MutableClock())) {log.record("GROUP","nome\n[ADMIN] falso\r\nlinha");log.flush();}
        assertEquals(1,Files.readAllLines(folder.resolve("logs/territorio-2026-11-17.log")).size());
    }
}
