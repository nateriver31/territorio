package dev.nateriver.territorio;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.logging.Level;

public class TerritorioPlugin extends JavaPlugin {
    private Domain domain;private StateStore store;private RaidService raids;private Commands commands;
    private FileConfiguration configuration;
    private UpdateOptions options;
    private ActivityLog activityLog;
    private DiscordAnnouncements discord;
    private final Set<UUID> bypass=new HashSet<>();
    private volatile boolean operational; private boolean loaded;
    @Override public void onEnable() {
        operational=false;loaded=false;
        new QuarantineListener().register(this);
        try {
            saveDefaultConfig();domain=new Domain();
            FileConfiguration candidate=readConfiguration();UpdateOptions next=UpdateOptions.read(candidate);validateConfig(candidate);
            store=new StateStore(getDataFolder().toPath(),error->{operational=false;getLogger().log(Level.SEVERE,"Falha de persistência: modo de manutenção ativo, interações bloqueadas. Pare o servidor e restaure o armazenamento antes de liberar jogadores.",error);});
            domain.s=store.load();loaded=true;domain.reindex();next.validateCapacity(domain);applyConfig(candidate,next);
            activityLog=new ActivityLog(getDataFolder().toPath(),next.retentionDays(),message->getLogger().warning(message));
            domain.onEvent=this::domainEvent;
            domain.onChange=()->store.save(domain.s);
            if(getDataFolder().toPath().resolve("data.yml").toFile().exists())getLogger().warning("data.yml da versão antiga foi preservado e NÃO será importado. Faça backup e remoção manual apenas quando desejar.");
            for(Domain.RaidRecord r:domain.s.raids)if(r.ended==0){r.ended=domain.now();r.result="REINICIO";}
            domain.changed();
            raids=new RaidService(this,domain);domain.activeVictim=raids::isVictim;
            discord=new DiscordAnnouncements(this);
            commands=new Commands(this,domain,raids);
            for(String name:List.of("territorio","claim","unclaim","claims","grupo","roubar","verchunk")) {
                PluginCommand command=Objects.requireNonNull(getCommand(name));command.setExecutor(commands);command.setTabCompleter(commands);
            }
            var resolved=Bukkit.getPluginCommand("t");
            if(resolved==null||resolved.getPlugin()!=this)getLogger().warning("/t pertence a outro plugin. Use /territorio ou /territorio:territorio. Nenhum comando externo foi sobrescrito.");
            Bukkit.getPluginManager().registerEvents(commands,this);Bukkit.getPluginManager().registerEvents(raids,this);Bukkit.getPluginManager().registerEvents(new ProtectionListener(this,domain,raids),this);
            Bukkit.getPluginManager().registerEvents(new ProgressListener(this,domain),this);
            operational=true;
            getLogger().info("Territorio "+getDescription().getVersion()+" iniciado; estado preservado. Homologue novas funções antes da produção.");
        }catch(Exception e){getLogger().log(Level.SEVERE,"Não foi possível iniciar com segurança.",e);operational=false;getLogger().severe("MODO DE MANUTENÇÃO: interações bloqueadas até corrigir os dados/configuração e reiniciar. Nenhum dado foi apagado.");}
    }
    private void validateConfig(FileConfiguration config) {
        int solo=config.getInt("claims.solo-limit",9),contribution=config.getInt("claims.group-per-member",9),min=config.getInt("groups.name-min",3),max=config.getInt("groups.name-max",32);
        if(solo<1||contribution<1||min<1||max<min||max>64)throw new IllegalArgumentException("Configuração de limites inválida");
    }
    private FileConfiguration readConfiguration() {
        try {YamlConfiguration config=new YamlConfiguration();config.load(getDataFolder().toPath().resolve("config.yml").toFile());return config;}
        catch(Exception e){throw new IllegalArgumentException("config.yml inválido; configuração anterior preservada.",e);}
    }
    private void applyConfig(FileConfiguration config,UpdateOptions next) {
        domain.soloLimit=config.getInt("claims.solo-limit",9);domain.contribution=config.getInt("claims.group-per-member",9);
        domain.nameMin=config.getInt("groups.name-min",3);domain.nameMax=config.getInt("groups.name-max",32);
        domain.s.bonusMaxPerMember=next.perMember();domain.bonusEnabled=next.bonus();domain.bonusPvpEnabled=next.bonusPvp();
        configuration=config;options=next;
    }
    public void reloadSafely() {
        FileConfiguration candidate=readConfiguration();UpdateOptions next=UpdateOptions.read(candidate);
        validateConfig(candidate);next.validateCapacity(domain);applyConfig(candidate,next);domain.changed();
        if(activityLog!=null)activityLog.retention(next.retentionDays());
    }
    public FileConfiguration settings(){return configuration;}
    UpdateOptions options(){return options;}
    DiscordAnnouncements discord(){return discord;}
    public boolean operational(){return operational&&store!=null&&store.healthy();}
    public Domain domain(){return domain;}
    public RaidService raids(){return raids;}
    public boolean bypassing(UUID p){var player=Bukkit.getPlayer(p);return bypass.contains(p)&&player!=null&&player.hasPermission("territorio.admin.bypass");}
    public boolean toggleBypass(UUID p){if(!bypass.add(p)){bypass.remove(p);return false;}return true;}
    java.util.concurrent.CompletableFuture<Void> storageCheckpoint(){return store.checkpoint();}
    public void afterSaved(Runnable action) {
        store.checkpoint().whenComplete((ignored,error)->{
            if(error==null&&isEnabled())Bukkit.getScheduler().runTask(this,()->{if(operational())action.run();});
        });
    }
    public void audit(String actor,String operation,String target,String before,String after){store.audit(actor,operation,target,before,after);activity("ADMIN",actor+" | "+operation+" | "+target+" | "+before+" -> "+after);}
    void activity(String category,String message){if(activityLog!=null)activityLog.record(category,message);}
    ActivityLog activityLog(){return activityLog;}
    String chunkDescription(Domain.Chunk chunk) {
        var world=Bukkit.getWorld(chunk.world());
        return "mundo="+(world==null?chunk.world():world.getName()+"/"+world.getKey())+" | centro X="+((long)chunk.x()*16+8)+" Z="+((long)chunk.z()*16+8)+" | chunk="+chunk.x()+","+chunk.z();
    }
    private void domainEvent(Domain.Change event) {
        activity(event.category(),event.message()+(event.lost()==null?"":" | "+chunkDescription(event.lost())));
        if(event.category().equals("BONUS_LOSS"))for(UUID id:event.recipients()) {
            var player=Bukkit.getPlayer(id);
            if(player!=null&&player.isOnline()) {
                player.sendMessage("§c[Território] Seu grupo perdeu um Claim Bônus!");
                player.sendMessage("§eChunk desprotegido: "+chunkDescription(event.lost()));
                player.sendMessage("§6Motivo: "+event.message());
            }
        }
    }
    @Override public void onDisable() {
        try{if(commands!=null)commands.close();if(raids!=null)raids.stopAll();}catch(Exception e){getLogger().log(Level.SEVERE,"Falha ao encerrar sessões do Território",e);}finally{bypass.clear();}
        if(store!=null)try{if(loaded&&domain!=null&&operational)store.save(domain.s);store.close();}catch(Exception e){getLogger().log(Level.SEVERE,"Falha ao concluir salvamento; preserve state.json e backups.",e);}
        if(activityLog!=null)try{activityLog.close();}catch(Exception e){getLogger().warning("Não foi possível concluir todos os logs diários do Território.");}
    }
}
