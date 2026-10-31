package dev.nateriver.territorio;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateStoreTest {
    @TempDir Path folder;

    @Test void completeStateRoundTripsChunkKeysCooldownsPeaceAndTrust() throws Exception {
        Domain d=new Domain(()->1_000_000L);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        Domain.Group ga=d.create(a,"Império Romano","ROMA"),gb=d.create(b,"Vikings","VIK");
        Domain.Chunk at=new Domain.Chunk(UUID.randomUUID(),-4,8);
        d.claim(a,at,false);d.trust(a,c,true);d.s.joinCooldown.put(c,9_000_000L);
        d.s.victimCooldown.put(a,43_200_000L);d.s.raiderCooldown.put(b,7_200_000L);
        d.declareWar(a,gb.id,p->true);d.peace(a,gb.id,"propor");
        AtomicReference<Throwable> error=new AtomicReference<>();
        try(StateStore store=new StateStore(folder,error::set)) {
            store.save(d.s);store.flush();
            Domain restored=new Domain();restored.s=store.load();restored.reindex();
            assertEquals(ga.id,restored.claim(at).owner);
            assertEquals(ga.id,restored.group(a).id);
            assertEquals(ga.id,restored.s.wars.getFirst().peaceFrom);
            assertEquals(43_200_000L,restored.s.victimCooldown.get(a));
            assertEquals(7_200_000L,restored.s.raiderCooldown.get(b));
            assertEquals(9_000_000L,restored.s.joinCooldown.get(c));
            assertTrue(restored.s.trusted.get(a).contains(c));assertNull(error.get());
        }
    }

    @Test void saveCapturesImmutableSnapshotAndRetainsPriorFileBackup() throws Exception {
        Domain d=new Domain();UUID a=UUID.randomUUID();d.s.names.put(a,"Primeiro");
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            store.save(d.s);d.s.names.put(a,"Segundo");store.flush();
            assertEquals("Primeiro",store.load().names.get(a));
            store.save(d.s);store.flush();
            assertEquals("Segundo",store.load().names.get(a));
            assertTrue(Files.readString(folder.resolve("state.json.bak")).contains("Primeiro"));
        }
    }

    @Test void invalidStateIsRejectedWithoutOverwritingItsBytes() throws Exception {
        Path file=folder.resolve("state.json");String damaged="{ this is corrupt";Files.writeString(file,damaged);
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            assertThrows(java.io.IOException.class,store::load);
            assertEquals(damaged,Files.readString(file));
        }
    }

    @Test void danglingGroupClaimAndDuplicateLeadershipAreRejected() {
        Domain d=new Domain();UUID a=UUID.randomUUID();Domain.Group g=d.create(a,"Grupo Teste","TES");
        Domain.Chunk at=new Domain.Chunk(UUID.randomUUID(),0,0);
        d.s.claims.put(at,new Domain.Claim(Domain.Kind.GROUP,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->StateStore.validate(d.s));
        d.s.claims.clear();UUID b=UUID.randomUUID();g.members.put(b,new Domain.Member(b,Domain.Role.LEADER,0));
        assertThrows(IllegalArgumentException.class,()->StateStore.validate(d.s));
    }

    @Test void auditIsOrderedAndContainsSeparateStructuredRecords() throws Exception {
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            store.audit("Admin","RENOMEAR","grupo","velho","novo");
            store.audit("Admin","CLAIM","chunk","ausente","presente");store.flush();
            List<String> lines=Files.readAllLines(folder.resolve("audit.jsonl"));
            assertEquals(2,lines.size());
            assertEquals("RENOMEAR",StateStore.JSON.fromJson(lines.get(0),com.google.gson.JsonObject.class).get("operation").getAsString());
            assertEquals("CLAIM",StateStore.JSON.fromJson(lines.get(1),com.google.gson.JsonObject.class).get("operation").getAsString());
        }
    }

    @Test void incompleteJsonCannotSilentlyResetTheServerState() throws Exception {
        Path file=folder.resolve("state.json");Files.writeString(file,"{}");
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            assertThrows(java.io.IOException.class,store::load);assertEquals("{}",Files.readString(file));
        }
    }
}
