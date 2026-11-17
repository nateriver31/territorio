package dev.nateriver.territorio;

import com.google.gson.JsonObject;
import java.nio.file.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only generated state in MockBukkit's temporary data folder; never reads production. */
class ProductionCopyTest extends UpdateTestBase {
    @Test void pluginStartsFromCompleteSchemaTwoCopyAndOldClaimsAndCommandsStillWork() throws Exception {
        groups();join();d.rank(a.getUniqueId(),member.getUniqueId(),Domain.Role.COLEADER);
        d.claim(a.getUniqueId(),at(0),false);d.claim(member.getUniqueId(),at(1),true);d.claim(outsider.getUniqueId(),at(4),false);
        d.trust(member.getUniqueId(),outsider.getUniqueId(),true);
        d.declareWar(a.getUniqueId(),d.group(b.getUniqueId()).id,id->true);d.peace(a.getUniqueId(),d.group(b.getUniqueId()).id,"propor");
        saved();server.getPluginManager().disablePlugin(plugin);
        var helper=new BonusPersistenceTest();JsonObject old=helper.legacy(d);String bytes=StateStore.JSON.toJson(old);
        Path file=plugin.getDataFolder().toPath().resolve("state.json");Files.writeString(file,bytes);
        server.getPluginManager().enablePlugin(plugin);assertTrue(plugin.operational());d=plugin.domain();saved();
        assertEquals(old,helper.legacy(d));assertEquals(bytes,Files.readString(file.resolveSibling("state.json.pre-v3.bak")));
        assertTrue(d.s.groups.values().stream().allMatch(g->g.bonusEarned==0));assertEquals(Domain.Kind.GROUP,d.claim(at(0)).kind);
        assertEquals(Domain.Kind.PERSONAL,d.claim(at(1)).kind);assertEquals(Domain.Kind.SOLO,d.claim(at(4)).kind);
        assertEquals(Domain.Role.COLEADER,d.group(member.getUniqueId()).members.get(member.getUniqueId()).role);
        assertTrue(d.s.trusted.get(member.getUniqueId()).contains(outsider.getUniqueId()));assertNotNull(d.s.wars.getFirst().peaceFrom);
        clearMessages();a.performCommand("t claims");assertTrue(String.join("\n",messages(a)).contains("Claims Bônus conquistados: 0/12"));
        var denied=new BlockBreakEvent(world.getBlockAt(24,64,8),b);server.getPluginManager().callEvent(denied);assertTrue(denied.isCancelled());
        StateStore.validate(d.s);
    }
    @Test void failedMigrationKeepsOriginalAndExistingBackupBytesAndMaintainsQuarantine() throws Exception {
        groups();saved();server.getPluginManager().disablePlugin(plugin);
        JsonObject old=new BonusPersistenceTest().legacy(d);old.getAsJsonObject("groups").getAsJsonObject(d.group(a.getUniqueId()).id.toString()).remove("members");
        Path file=plugin.getDataFolder().toPath().resolve("state.json"),backup=file.resolveSibling("state.json.bak");
        String bytes=old.toString();Files.writeString(file,bytes);Files.writeString(backup,"previous valid external test backup");
        server.getPluginManager().enablePlugin(plugin);assertFalse(plugin.operational());
        assertEquals(bytes,Files.readString(file));assertEquals("previous valid external test backup",Files.readString(backup));
        assertFalse(Files.exists(file.resolveSibling("state.json.pre-v3.bak")));
        var guarded=new BlockBreakEvent(world.getBlockAt(24,64,8),b);server.getPluginManager().callEvent(guarded);assertTrue(guarded.isCancelled());
        server.getPluginManager().disablePlugin(plugin);assertEquals(bytes,Files.readString(file));
    }
    @Test void restartPreservesEarnedBalanceAndClaimOrderWithoutRetroactivePvpLosses() throws Exception {
        groups();join();earn(4);d.claimBonus(a.getUniqueId(),at(0));d.claimBonus(a.getUniqueId(),at(1));d.unclaim(a.getUniqueId(),at(0));d.claimBonus(a.getUniqueId(),at(3));
        saved();server.getPluginManager().disablePlugin(plugin);server.getPluginManager().enablePlugin(plugin);assertTrue(plugin.operational());d=plugin.domain();
        assertEquals(4,d.group(a.getUniqueId()).bonusEarned);assertEquals(3,d.group(a.getUniqueId()).bonusSequence);
        d.pvpKill(b.getUniqueId(),a.getUniqueId());d.pvpKill(b.getUniqueId(),a.getUniqueId());assertNotNull(d.claim(at(3)));
        d.pvpKill(b.getUniqueId(),a.getUniqueId());assertNull(d.claim(at(3)));assertNotNull(d.claim(at(1)));StateStore.validate(d.s);
    }
}
