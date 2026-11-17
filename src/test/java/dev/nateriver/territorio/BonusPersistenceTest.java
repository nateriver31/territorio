package dev.nateriver.territorio;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BonusPersistenceTest {
    @TempDir Path folder;
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),member=UUID.randomUUID(),world=UUID.randomUUID();
    Domain fixture(){
        Domain d=new Domain(()->1_000_000L);
        Domain.Group one=d.create(a,"Roma","ROMA"),two=d.create(b,"Vikings","VIK");
        d.invite(a,member);d.accept(member);d.rank(a,member,Domain.Role.COLEADER);
        d.claim(a,new Domain.Chunk(world,-2,7),false);d.claim(member,new Domain.Chunk(world,5,-8),true);
        d.trust(member,b,true);d.s.names.put(a,"Antigo");d.s.raiderCooldown.put(a,5_000_000L);d.s.victimCooldown.put(b,7_000_000L);
        d.ally(a,two.id,"propor");d.ally(b,one.id,"aceitar");d.allyPermission(a,two.id,Domain.Access.CONTAINERS,true);
        return d;
    }
    JsonObject legacy(Domain d){
        JsonObject raw=StateStore.JSON.toJsonTree(d.s).getAsJsonObject();raw.addProperty("schemaVersion",2);raw.remove("bonusMaxPerMember");
        for(var entry:raw.getAsJsonObject("groups").entrySet()) {entry.getValue().getAsJsonObject().remove("bonusEarned");entry.getValue().getAsJsonObject().remove("bonusSequence");}
        if(raw.get("claims").isJsonArray())for(JsonElement entry:raw.getAsJsonArray("claims"))entry.getAsJsonArray().get(1).getAsJsonObject().remove("bonusOrder");
        return raw;
    }
    @Test void migratesACopyOfCompleteLegacyStateWithZeroBonusAndUntouchedOriginalUntilSave() throws Exception {
        Domain original=fixture();JsonObject old=legacy(original);String bytes=StateStore.JSON.toJson(old);
        Path file=folder.resolve("state.json");Files.writeString(file,bytes);
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            Domain.State migrated=store.load();
            assertEquals(bytes,Files.readString(file));assertEquals(bytes,Files.readString(folder.resolve("state.json.pre-v3.bak")));
            assertEquals(3,migrated.schemaVersion);assertTrue(migrated.groups.values().stream().allMatch(g->g.bonusEarned==0&&g.bonusSequence==0));
            Domain comparison=new Domain();comparison.s=migrated;
            assertEquals(old,legacy(comparison),"Every legacy field, group, membership and claim must survive");
            assertTrue(migrated.claims.values().stream().noneMatch(c->c.kind==Domain.Kind.BONUS));
            store.save(migrated);store.flush();
            assertEquals(bytes,Files.readString(folder.resolve("state.json.pre-v3.bak")));
            assertEquals(bytes,Files.readString(folder.resolve("state.json.bak")));
            assertEquals(3,store.load().schemaVersion);
        }
    }
    @Test void activeWarsPendingPeaceAndOtherGroupsAlliancesSurviveMigration() throws Exception {
        Domain d=fixture();UUID c=UUID.randomUUID(),e=UUID.randomUUID();
        var third=d.create(c,"Terceiro","TER");var fourth=d.create(e,"Quarto","QUA");
        d.declareWar(c,fourth.id,id->true);d.peace(c,fourth.id,"propor");
        String old=StateStore.JSON.toJson(legacy(d));Files.writeString(folder.resolve("state.json"),old);
        try(StateStore store=new StateStore(folder,e2->fail(e2))) {
            Domain restored=new Domain();restored.s=store.load();restored.reindex();
            assertTrue(restored.inWar(third.id));assertEquals(third.id,restored.s.wars.getFirst().peaceFrom);
            assertTrue(restored.group(a).allies.containsKey(restored.group(b).id));
            assertEquals(Domain.Role.COLEADER,restored.group(member).members.get(member).role);
            assertTrue(restored.s.trusted.get(member).contains(b));StateStore.validate(restored.s);
        }
    }
    @Test void badLegacyMigrationNeverCreatesAnEmptyReplacementOrTouchesOriginal() throws Exception {
        Domain d=fixture();JsonObject raw=legacy(d);raw.getAsJsonObject("groups").entrySet().iterator().next().getValue().getAsJsonObject().remove("members");
        String bytes=raw.toString();Path file=folder.resolve("state.json");Files.writeString(file,bytes);
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            assertThrows(java.io.IOException.class,store::load);assertEquals(bytes,Files.readString(file));
            assertFalse(Files.exists(folder.resolve("state.json.pre-v3.bak")));
        }
    }
    @Test void invalidBackupDestinationFailsWithoutReplacingSource() throws Exception {
        String bytes=legacy(fixture()).toString();Files.writeString(folder.resolve("state.json"),bytes);Files.createDirectory(folder.resolve("state.json.pre-v3.bak"));
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            assertThrows(java.io.IOException.class,store::load);assertEquals(bytes,Files.readString(folder.resolve("state.json")));
        }
    }
    @Test void chronologicalOrderAndBalancesSurviveRestartAndLifoStillReleasesNewest() throws Exception {
        Domain d=fixture();for(int i=0;i<8;i++)d.pvpKill(a,b);
        for(int i=0;i<8;i++)d.claimBonus(a,new Domain.Chunk(world,i,20));
        d.unclaim(a,new Domain.Chunk(world,3,20));d.claimBonus(a,new Domain.Chunk(world,30,20));
        try(StateStore store=new StateStore(folder,e->fail(e))) {
            store.save(d.s);store.flush();Domain restored=new Domain();restored.s=store.load();restored.reindex();
            assertEquals(8,restored.group(a).bonusEarned);assertEquals(9,restored.group(a).bonusSequence);
            restored.pvpKill(b,a);assertNull(restored.claim(new Domain.Chunk(world,30,20)));
            assertNotNull(restored.claim(new Domain.Chunk(world,7,20)));assertEquals(7,restored.count(Domain.Kind.BONUS,restored.group(a).id));
            StateStore.validate(restored.s);
        }
    }
    @Test void truncatedNewBonusFieldsCannotSilentlyResetExistingBalances() throws Exception {
        Domain d=fixture();d.pvpKill(a,b);JsonObject raw=StateStore.JSON.toJsonTree(d.s).getAsJsonObject();
        raw.getAsJsonObject("groups").getAsJsonObject(d.group(a).id.toString()).remove("bonusEarned");
        String bytes=raw.toString();Files.writeString(folder.resolve("state.json"),bytes);
        try(StateStore store=new StateStore(folder,e->fail(e))) {assertThrows(java.io.IOException.class,store::load);assertEquals(bytes,Files.readString(folder.resolve("state.json")));}
    }
    @Test void invalidBonusBalancesOrdersAndUnknownSchemaAreRejected() throws Exception {
        Domain d=fixture();var g=d.group(a);g.bonusEarned=-1;assertThrows(IllegalArgumentException.class,()->StateStore.validate(d.s));
        g.bonusEarned=13;assertThrows(IllegalArgumentException.class,()->StateStore.validate(d.s));g.bonusEarned=1;
        d.claimBonus(a,new Domain.Chunk(world,0,20));d.s.claims.put(new Domain.Chunk(world,1,20),d.claim(new Domain.Chunk(world,0,20)));
        assertThrows(IllegalArgumentException.class,()->StateStore.validate(d.s));
        JsonObject old=legacy(fixture());old.addProperty("schemaVersion",2.5);Files.writeString(folder.resolve("state.json"),old.toString());
        try(StateStore store=new StateStore(folder,e->fail(e))) {assertThrows(java.io.IOException.class,store::load);}
    }
    @Test void asyncWriteFailureKeepsLastValidStateAndReportsFailure() throws Exception {
        Domain d=fixture();AtomicReference<Throwable> error=new AtomicReference<>();StateStore store=new StateStore(folder,error::set);
        store.save(d.s);store.flush();String saved=Files.readString(folder.resolve("state.json"));
        Files.createDirectory(folder.resolve("state.json.tmp"));d.pvpKill(a,b);store.save(d.s);
        assertThrows(Exception.class,store::flush);assertNotNull(error.get());assertEquals(saved,Files.readString(folder.resolve("state.json")));
        assertThrows(Exception.class,store::close);
    }
    @Test void fractionalOrOverflowedBonusNumbersAreRejectedWithoutTruncatingTheSavedState() throws Exception {
        for(String key:List.of("bonusEarned","bonusSequence"))for(Number bad:List.of(0.5,new java.math.BigDecimal("999999999999999999999999"))) {
            Domain d=fixture();JsonObject raw=StateStore.JSON.toJsonTree(d.s).getAsJsonObject();
            raw.getAsJsonObject("groups").getAsJsonObject(d.group(a).id.toString()).addProperty(key,bad);
            String bytes=raw.toString();Files.writeString(folder.resolve("state.json"),bytes);
            try(StateStore store=new StateStore(folder,e->fail(e))) {
                assertThrows(java.io.IOException.class,store::load);assertEquals(bytes,Files.readString(folder.resolve("state.json")));
            }
        }
        Domain d=fixture();d.pvpKill(a,b);d.claimBonus(a,new Domain.Chunk(world,0,20));
        JsonObject raw=StateStore.JSON.toJsonTree(d.s).getAsJsonObject();
        raw.getAsJsonArray("claims").get(0).getAsJsonArray().get(1).getAsJsonObject().addProperty("bonusOrder",1.5);
        Files.writeString(folder.resolve("state.json"),raw.toString());
        try(StateStore store=new StateStore(folder,e->fail(e))) {assertThrows(java.io.IOException.class,store::load);}
    }
}
