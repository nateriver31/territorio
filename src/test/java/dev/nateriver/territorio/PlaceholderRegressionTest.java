package dev.nateriver.territorio;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.nateriver.territorio.Domain.Role.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaceholderRegressionTest {
    ServerMock server;
    Domain domain;
    TerritoryExpansion expansion;
    PlayerMock leader, member;

    @BeforeEach void setup(){
        server=MockBukkit.mock();
        leader=server.addPlayer("Lider");member=server.addPlayer("Membro");
        domain=new Domain();expansion=new TerritoryExpansion(domain);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    private Domain.Group group(){
        Domain.Group group=domain.create(leader.getUniqueId(),"Império Romano","ROMA");
        domain.invite(leader.getUniqueId(),member.getUniqueId());domain.accept(member.getUniqueId());
        expansion.refresh();return group;
    }
    private String value(PlayerMock player,String parameter){return expansion.onRequest(player,parameter);}
    private void emptyPrefix(PlayerMock player){
        assertEquals("",value(player,"tag_formatada"));assertEquals("",value(player,"cargo_simbolo"));
        assertEquals("",value(player,"grupo"));assertEquals("",value(player,"cargo"));
        assertEquals("false",value(player,"tem_grupo"));
    }

    @Test void soloHasNoPrefixOrExtraSpacesAndExistingPlaceholdersKeepTheirContract(){
        emptyPrefix(leader);assertEquals("",value(leader,"tag"));
        assertEquals("",expansion.onRequest(null,"cargo_simbolo"));
        assertNull(value(leader,"desconhecido"));
        group();
        assertEquals("ROMA",value(leader,"tag"));
        assertEquals("§6[ROMA]§r ",value(leader,"TAG_FORMATADA"));
        assertEquals("Império Romano",value(leader,"grupo"));
        assertEquals("Líder",value(leader,"cargo"));assertEquals("true",value(leader,"tem_grupo"));
        assertEquals("§6♛§r §6[ROMA]§r §aApelido",
                value(leader,"cargo_simbolo")+value(leader,"tag_formatada")+"§aApelido");
    }

    @Test void symbolsDistinguishRanksWithoutAddingAnythingToMemberTag(){
        group();
        assertEquals("§6♛§r ",value(leader,"cargo_simbolo"));
        assertEquals("",value(member,"cargo_simbolo"));
        domain.rank(leader.getUniqueId(),member.getUniqueId(),OFFICER);expansion.refresh();
        assertEquals("§6•§r ",value(member,"cargo_simbolo"));
        domain.rank(leader.getUniqueId(),member.getUniqueId(),COLEADER);expansion.refresh();
        assertEquals("§6◆§r ",value(member,"cargo_simbolo"));
        domain.rank(leader.getUniqueId(),member.getUniqueId(),MEMBER);expansion.refresh();
        assertEquals("",value(member,"cargo_simbolo"));
        assertEquals("§6[ROMA]§r ",value(member,"tag_formatada"));
    }

    @Test void membershipLeadershipAndAdministrativeChangesAreVisibleAfterRefresh(){
        Domain.Group group=group();
        domain.adminRename(group.id,"Roma Nova","NOVA");expansion.refresh();
        assertEquals("§6[NOVA]§r ",value(member,"tag_formatada"));
        assertEquals("Roma Nova",value(leader,"grupo"));
        domain.rank(leader.getUniqueId(),member.getUniqueId(),LEADER);expansion.refresh();
        assertEquals("",value(leader,"cargo_simbolo"));
        assertEquals("§6♛§r ",value(member,"cargo_simbolo"));
        domain.adminLeader(group.id,leader.getUniqueId());expansion.refresh();
        assertEquals("§6♛§r ",value(leader,"cargo_simbolo"));
        assertEquals("",value(member,"cargo_simbolo"));
        domain.kick(leader.getUniqueId(),member.getUniqueId());expansion.refresh();emptyPrefix(member);
        domain.invite(leader.getUniqueId(),member.getUniqueId());domain.accept(member.getUniqueId());expansion.refresh();
        assertEquals("NOVA",value(member,"tag"));
        domain.leave(leader.getUniqueId());expansion.refresh();emptyPrefix(leader);
        assertEquals("§6♛§r ",value(member,"cargo_simbolo"));
        domain.dissolve(member.getUniqueId());expansion.refresh();emptyPrefix(member);
    }

    @Test void asynchronousReadsUsePublishedSnapshotUntilNextRefresh() throws Exception {
        Domain.Group group=group();
        domain.adminRename(group.id,"Roma Nova","NOVA");
        assertEquals(List.of("ROMA","Império Romano","§6♛§r "),
                CompletableFuture.supplyAsync(()->List.of(value(leader,"tag"),value(leader,"grupo"),value(leader,"cargo_simbolo"))).get(5,TimeUnit.SECONDS));
        expansion.refresh();
        assertEquals("NOVA",CompletableFuture.supplyAsync(()->value(leader,"tag")).get(5,TimeUnit.SECONDS));
    }
}
