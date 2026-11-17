package dev.nateriver.territorio;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

abstract class UpdateTestBase {
    ServerMock server;TerritorioPlugin plugin;Domain d;WorldMock world;
    PlayerMock a,b,member,outsider;
    @BeforeEach void setupUpdate() {
        server=MockBukkit.mock();world=server.addSimpleWorld("update");plugin=MockBukkit.load(TerritorioPlugin.class);
        Assertions.assertTrue(plugin.operational());d=plugin.domain();
        a=server.addPlayer("Romano");b=server.addPlayer("Viking");member=server.addPlayer("Membro");outsider=server.addPlayer("Solo");
        for(var player:List.of(a,b,member,outsider))player.teleport(new Location(world,40,65,8));
        server.getScheduler().performTicks(2);clearMessages();
    }
    @AfterEach void cleanupUpdate(){MockBukkit.unmock();}
    void groups(){d.create(a.getUniqueId(),"Imperio Romano","ROMA");d.create(b.getUniqueId(),"Vikings","VIK");}
    Domain.Chunk at(int x){return new Domain.Chunk(world.getUID(),x,0);}
    void join(){d.invite(a.getUniqueId(),member.getUniqueId());d.accept(member.getUniqueId());}
    void earn(int n){for(int i=0;i<n;i++)d.pvpKill(a.getUniqueId(),b.getUniqueId());}
    void configure(String key,Object value) throws Exception {
        var file=plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        var config=YamlConfiguration.loadConfiguration(file);config.set(key,value);config.save(file);plugin.reloadSafely();
    }
    List<String> messages(PlayerMock player){List<String> all=new ArrayList<>();String next;while((next=player.nextMessage())!=null)all.add(ChatColor.stripColor(next));return all;}
    void clearMessages(){for(var player:List.of(a,b,member,outsider))messages(player);}
    void saved() throws Exception {plugin.storageCheckpoint().get(5,java.util.concurrent.TimeUnit.SECONDS);server.getScheduler().performTicks(2);}
    PlayerDeathEvent death(Player killer,Player victim,DamageType type){
        var builder=DamageSource.builder(type);if(killer!=null)builder.withCausingEntity(killer).withDirectEntity(killer);
        return new PlayerDeathEvent(victim,builder.build(),new ArrayList<>(),0,Component.empty(),true);
    }
    PlayerRespawnEvent respawn(Player player){return new PlayerRespawnEvent(player,player.getLocation(),false,false,false,PlayerRespawnEvent.RespawnReason.DEATH);}
}
