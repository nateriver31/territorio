package dev.nateriver.territorio;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.logging.Level;

public class TerritorioPlugin extends JavaPlugin {
    private Domain domain;private StateStore store;private RaidService raids;private Commands commands;
    private final Set<UUID> bypass=new HashSet<>();
    private volatile boolean operational; private boolean loaded;
    @Override public void onEnable() {
        operational=false;loaded=false;
        new QuarantineListener().register(this);
        try {
            saveDefaultConfig();domain=new Domain();applyConfig();
            store=new StateStore(getDataFolder().toPath(),error->{operational=false;getLogger().log(Level.SEVERE,"Falha de persistência: modo de manutenção ativo, interações bloqueadas. Pare o servidor e restaure o armazenamento antes de liberar jogadores.",error);});
            domain.s=store.load();loaded=true;domain.reindex();
            domain.onChange=()->store.save(domain.s);
            if(getDataFolder().toPath().resolve("data.yml").toFile().exists())getLogger().warning("data.yml da versão antiga foi preservado e NÃO será importado. Faça backup e remoção manual apenas quando desejar.");
            for(Domain.RaidRecord r:domain.s.raids)if(r.ended==0){r.ended=domain.now();r.result="REINICIO";}
            domain.changed();
            raids=new RaidService(this,domain);domain.activeVictim=raids::isVictim;
            commands=new Commands(this,domain,raids);
            for(String name:List.of("territorio","claim","unclaim","claims","grupo","roubar","verchunk")) {
                PluginCommand command=Objects.requireNonNull(getCommand(name));command.setExecutor(commands);command.setTabCompleter(commands);
            }
            var resolved=Bukkit.getPluginCommand("t");
            if(resolved==null||resolved.getPlugin()!=this)getLogger().warning("/t pertence a outro plugin. Use /territorio ou /territorio:territorio. Nenhum comando externo foi sobrescrito.");
            Bukkit.getPluginManager().registerEvents(commands,this);Bukkit.getPluginManager().registerEvents(raids,this);Bukkit.getPluginManager().registerEvents(new ProtectionListener(this,domain,raids),this);
            operational=true;
            getLogger().info("Territorio 2.0 iniciado. PvP e configurações externas permanecem sob controle do servidor.");
        }catch(Exception e){getLogger().log(Level.SEVERE,"Não foi possível iniciar com segurança.",e);operational=false;getLogger().severe("MODO DE MANUTENÇÃO: interações bloqueadas até corrigir os dados/configuração e reiniciar. Nenhum dado foi apagado.");}
    }
    private void applyConfig() {
        int solo=getConfig().getInt("claims.solo-limit",9),contribution=getConfig().getInt("claims.group-per-member",9),min=getConfig().getInt("groups.name-min",3),max=getConfig().getInt("groups.name-max",32);
        if(solo<1||contribution<1||min<1||max<min||max>64)throw new IllegalArgumentException("Configuração de limites inválida");
        domain.soloLimit=solo;domain.contribution=contribution;domain.nameMin=min;domain.nameMax=max;
    }
    public void reloadSafely() {reloadConfig();applyConfig();}
    public FileConfiguration settings(){return getConfig();}
    public boolean operational(){return operational;}
    public Domain domain(){return domain;}
    public RaidService raids(){return raids;}
    public boolean bypassing(UUID p){var player=Bukkit.getPlayer(p);return bypass.contains(p)&&player!=null&&player.hasPermission("territorio.admin.bypass");}
    public boolean toggleBypass(UUID p){if(!bypass.add(p)){bypass.remove(p);return false;}return true;}
    java.util.concurrent.CompletableFuture<Void> storageCheckpoint(){return store.checkpoint();}
    public void afterSaved(Runnable action) {
        store.checkpoint().whenComplete((ignored,error)->{
            if(error==null&&isEnabled())Bukkit.getScheduler().runTask(this,()->{if(operational)action.run();});
        });
    }
    public void audit(String actor,String operation,String target,String before,String after){store.audit(actor,operation,target,before,after);}
    @Override public void onDisable() {
        try{if(commands!=null)commands.close();if(raids!=null)raids.stopAll();}catch(Exception e){getLogger().log(Level.SEVERE,"Falha ao encerrar sessões do Território",e);}finally{bypass.clear();}
        if(store!=null)try{if(loaded&&domain!=null&&operational)store.save(domain.s);store.close();}catch(Exception e){getLogger().log(Level.SEVERE,"Falha ao concluir salvamento; preserve state.json e backups.",e);}
    }
}
