package dev.nateriver.territorio;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CommandsTest {
    ServerMock server;
    TerritorioPlugin plugin;
    WorldMock world;
    PlayerMock a,b,c;

    @BeforeEach void setup() {
        server=MockBukkit.mock(); world=server.addSimpleWorld("commands");
        plugin=MockBukkit.load(TerritorioPlugin.class);
        a=server.addPlayer("Romano"); b=server.addPlayer("Viking"); c=server.addPlayer("Egipcio");
        for(PlayerMock p:List.of(a,b,c))p.teleport(new Location(world,40,65,8));
        server.getScheduler().performTicks(2);
        for(PlayerMock p:List.of(a,b,c))messages(p);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}

    private Domain d(){return plugin.domain();}
    private Domain.Group group(PlayerMock p){return d().group(p.getUniqueId());}
    private List<String> messages(PlayerMock p) {
        List<String> out=new ArrayList<>(); String message;
        while((message=p.nextMessage())!=null)out.add(ChatColor.stripColor(message));
        return out;
    }
    private List<String> complete(PlayerMock p,String... args) {
        return Objects.requireNonNull(plugin.getCommand("territorio")).tabComplete(p,"t",args);
    }
    private void opposingGroups() {
        a.performCommand("t grupo criar ROMA Império Romano");
        b.performCommand("t criar VIK Vikings");
    }

    @Test void groupNamespaceAndLegacyCommandsShareHierarchyAndMembership() {
        a.performCommand("t grupo criar ROMA Império Romano");
        Domain.Group g=group(a);
        assertEquals("Império Romano",g.name);
        a.performCommand("t grupo convidar Viking"); b.performCommand("t grupo recusar");
        assertFalse(d().s.invites.containsKey(b.getUniqueId()));
        a.performCommand("t convidar Viking"); b.performCommand("t aceitar");
        assertSame(g,group(b));
        a.performCommand("t grupo cargo Viking oficial");
        assertEquals(Domain.Role.OFFICER,g.members.get(b.getUniqueId()).role);
        b.performCommand("t grupo cargo Romano membro");
        assertEquals(Domain.Role.LEADER,g.members.get(a.getUniqueId()).role);
        a.performCommand("t grupo cargo Viking colider");
        a.performCommand("t grupo lider Viking confirmar");
        assertEquals(b.getUniqueId(),g.leader);
        b.performCommand("t grupo expulsar Romano");
        assertNull(group(a)); assertFalse(d().s.joinCooldown.containsKey(a.getUniqueId()));
        b.performCommand("t grupo dissolver confirmar");
        assertTrue(d().s.groups.isEmpty());
    }

    @Test void groupNamespaceRetainsPermissionsAndDoesNotRouteAdministration() {
        a.addAttachment(plugin,"territorio.grupo",false);
        a.performCommand("t grupo criar ROMA Império Romano");
        assertNull(group(a));
        b.performCommand("t grupo criar ROMA Império Romano");
        a.addAttachment(plugin,"territorio.grupo",true);
        a.addAttachment(plugin,"territorio.admin.rename",true);
        a.performCommand("t grupo admin tag ROMA NOVA");
        assertEquals("ROMA",group(b).tag);
        a.performCommand("t admin tag ROMA NOVA");
        assertEquals("NOVA",group(b).tag);
        assertFalse(complete(a,"grupo","").contains("admin"));
    }

    @Test void shortWarCommandIsUnilateralAndPreservesAdministrativeWarRestrictions() {
        opposingGroups();
        a.performCommand("t guerra VIK");
        assertEquals(1,d().s.wars.size());
        Domain.War war=d().s.wars.getFirst();
        assertEquals(group(a).id,war.attacker); assertEquals(group(b).id,war.defender); assertEquals(0,war.ended);
        a.performCommand("t grupo sair");
        assertNotNull(group(a));
        messages(a); a.performCommand("t guerra status");
        assertTrue(messages(a).stream().anyMatch(m->m.contains("ATIVA")));
        a.performCommand("t guerra historico");
        assertTrue(messages(a).stream().anyMatch(m->m.contains("Histórico de guerras")));
        a.performCommand("t guerras");
        assertTrue(messages(a).stream().anyMatch(m->m.contains("Histórico de guerras")));
        assertEquals(1,d().s.wars.size());
    }

    @Test void bothDeclarationFormsRequireDefenderLeaderOnlineWithoutDeferredStart() {
        opposingGroups(); b.disconnect();
        a.performCommand("t guerra VIK"); a.performCommand("t guerra declarar VIK");
        assertTrue(d().s.wars.isEmpty());
        assertTrue(messages(a).stream().anyMatch(m->m.contains("dois líderes precisam estar online")));
    }

    @Test void pazTagAndLegacyPeaceAliasHaveDistinctMeaning() {
        a.performCommand("t grupo criar ROMA Império Romano");
        b.performCommand("t grupo criar PAZ Grupo Pacifico");
        a.performCommand("t guerra PAZ");
        assertEquals(1,d().s.wars.size());
        a.performCommand("t guerra paz PAZ");
        assertEquals(group(a).id,d().s.wars.getFirst().peaceFrom);
        b.performCommand("t guerra recusarpaz ROMA");
        assertNull(d().s.wars.getFirst().peaceFrom);
        a.performCommand("t paz PAZ"); b.performCommand("t guerra aceitarpaz ROMA");
        assertEquals("ACORDO_DE_PAZ",d().s.wars.getFirst().result);
    }

    @Test void oneIncomingPeaceOfferCanBeRefusedOrAcceptedWithoutTag() {
        opposingGroups(); a.performCommand("t guerra declarar VIK");
        b.performCommand("t paz aceitar"); assertEquals(0,d().s.wars.getFirst().ended);
        a.performCommand("t paz VIK");
        a.performCommand("t paz aceitar"); assertEquals(group(a).id,d().s.wars.getFirst().peaceFrom);
        b.performCommand("t paz recusar"); assertNull(d().s.wars.getFirst().peaceFrom);
        assertEquals(0,d().s.wars.getFirst().ended);
        a.performCommand("t paz propor VIK"); b.performCommand("t paz aceitar");
        assertEquals("ACORDO_DE_PAZ",d().s.wars.getFirst().result);
    }

    @Test void multipleIncomingPeaceOffersRequireTargetAndLeaderAuthority() throws Exception {
        opposingGroups(); c.performCommand("t grupo criar EGI Egito");
        a.performCommand("t guerra VIK"); c.performCommand("t guerra VIK");
        a.performCommand("t paz VIK"); c.performCommand("t paz VIK");
        messages(b);
        b.performCommand("t paz aceitar"); b.performCommand("t paz recusar");
        assertTrue(d().s.wars.stream().allMatch(w->w.ended==0&&w.peaceFrom!=null));
        List<String> ambiguous=messages(b);
        assertEquals(2,ambiguous.size()); assertTrue(ambiguous.stream().allMatch(m->m.contains("EGI")&&m.contains("ROMA")));
        PlayerMock member=server.addPlayer("Membro");
        // Joining is intentionally blocked during war; seed this test's existing membership instead.
        group(b).members.put(member.getUniqueId(),new Domain.Member(member.getUniqueId(),Domain.Role.MEMBER,d().now())); d().changed();
        member.performCommand("t paz aceitar ROMA");
        assertTrue(d().s.wars.stream().allMatch(w->w.ended==0));
        plugin.storageCheckpoint().get(5,java.util.concurrent.TimeUnit.SECONDS);
        server.getPluginManager().disablePlugin(plugin); server.getPluginManager().enablePlugin(plugin);
        assertEquals(2,d().s.wars.stream().filter(w->w.peaceFrom!=null).count());
        b.performCommand("t paz aceitar ROMA");
        assertEquals(1,d().s.wars.stream().filter(w->w.ended==0).count());
        b.performCommand("t paz recusar");
        assertTrue(d().s.wars.stream().allMatch(w->w.peaceFrom==null));
    }

    @Test void completionSupportsNamespacesShortFormsAndReceivedOffers() {
        opposingGroups();
        assertTrue(complete(a,"gr").contains("grupo"));
        assertTrue(complete(a,"grupo","").containsAll(List.of("convidar","cargo","lider","info")));
        assertTrue(complete(a,"grupo","cargo","Viking","").containsAll(List.of("membro","oficial","colider")));
        assertTrue(complete(a,"guerra","").containsAll(List.of("VIK","status","historico","declarar")));
        assertTrue(complete(a,"paz","").containsAll(List.of("VIK","aceitar","recusar","propor")));
        a.performCommand("t guerra VIK"); a.performCommand("t paz VIK");
        assertEquals(List.of("ROMA"),complete(b,"paz","aceitar",""));
        assertFalse(complete(a,"grupo","").contains("sair"));
        c.addAttachment(plugin,"territorio.grupo",false);
        assertTrue(complete(c,"grupo","").isEmpty());
    }

    @Test void noticesIncludeGroupNameStayPrivateAndDoNotRepeatAcrossItsChunks() {
        a.performCommand("t grupo criar ROMA Império Romano");
        d().claim(a.getUniqueId(),new Domain.Chunk(world.getUID(),0,0),false);
        d().claim(a.getUniqueId(),new Domain.Chunk(world.getUID(),1,0),false);
        for(PlayerMock p:List.of(a,b,c))messages(p);
        b.teleport(new Location(world,8,65,8)); server.getScheduler().performTicks(2);
        assertEquals(List.of("> Você entrou no território do grupo [ROMA] Império Romano."),messages(b));
        assertTrue(messages(a).isEmpty()); assertTrue(messages(c).isEmpty());
        b.teleport(new Location(world,24,65,8)); server.getScheduler().performTicks(2);
        assertTrue(messages(b).isEmpty());
        b.teleport(new Location(world,40,65,8)); server.getScheduler().performTicks(2);
        assertEquals(List.of("< Você saiu do território do grupo [ROMA] Império Romano."),messages(b));
    }

    @Test void noticesPreserveIndividualOwnerIdentification() {
        d().claim(a.getUniqueId(),new Domain.Chunk(world.getUID(),0,0),false);
        b.teleport(new Location(world,8,65,8)); server.getScheduler().performTicks(2);
        assertEquals(List.of("> Você entrou no território de Romano."),messages(b));
    }

    // A conservative text estimate; actual Java and Bedrock font rendering still needs client tests.
    private static int estimatedWrappedLines(String text) {
        int lines=0;
        for(String paragraph:text.split("\n",-1)) {
            String remaining=paragraph;
            while(remaining.length()>22) {
                int cut=remaining.lastIndexOf(' ',22);
                if(cut<=0)cut=22;
                lines++; remaining=remaining.substring(cut).stripLeading();
            }
            lines++;
        }
        return lines;
    }

    @Test void manualDocumentsApprovedRulesAndConfiguredClaimLimitsWithNeutralName() {
        List<String> pages=Commands.manualPages(17,23).stream().map(p->PlainTextComponentSerializer.plainText().serialize(p)).toList();
        String text=String.join("\n",pages);
        assertTrue(text.contains("até 17 chunks")); assertTrue(text.contains("23 chunks coletivos"));
        assertTrue(text.contains("Declaração unilateral")); assertTrue(text.contains("não depende de aceitação"));
        assertTrue(text.contains("Mesmo grupo não causa PvP"));
        assertTrue(text.contains("autoria perdida ao recarregar chunks"));
        assertTrue(text.contains("circuitos acionados pelo invasor podem abri-los"));
        assertTrue(text.contains("portas ou alçapões manualmente"));
        assertTrue(text.contains("até 30 blocos")); assertFalse(text.contains("Survival"));
        assertTrue(pages.stream().allMatch(p->p.length()<300));
        for(String page:pages)assertTrue(estimatedWrappedLines(page)<=13,page);
    }
}
