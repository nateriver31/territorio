package dev.nateriver.territorio;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/** Optional public DiscordSRV/JDA API adapter. No bot, webhook, token or network thread of our own. */
final class DiscordAnnouncements {
    interface Transport {CompletableFuture<Void> send(String channel,String text);}
    private final TerritorioPlugin plugin;
    private final Set<String> announced=new HashSet<>();
    private Transport transport;
    DiscordAnnouncements(TerritorioPlugin plugin) {this.plugin=plugin;this.transport=new DiscordSrvTransport(plugin);}
    void transport(Transport transport) {this.transport=transport;}
    void war(Domain.War war,Domain.Group first,Domain.Group second,boolean peace) {
        UpdateOptions options=plugin.options();
        if(!options.discord()||(peace?!options.peace():!options.war()))return;
        String key=war.id+":"+peace;
        if(!announced.add(key))return;
        String a=label(first),b=label(second);
        String text=peace?"🕊️ PAZ ESTABELECIDA!\n\nOs grupos "+a+" e "+b+" encerraram oficialmente sua guerra.\n\nO conflito chegou ao fim.":
            "⚔️ GUERRA DECLARADA!\n\nO grupo "+a+" declarou guerra contra "+b+"!\n\nO conflito entre os dois grupos começou.";
        // Called only on an actual transition. Nothing is replayed on enable/reload.
        plugin.afterSaved(()->send(text,null));
    }
    void test(CommandSender sender) {
        if(!plugin.options().discord())throw new IllegalArgumentException("Integração DiscordSRV desativada na configuração.");
        sender.sendMessage(Component.text("[Território] Enviando teste ao Discord…"));
        send("✅ Teste de integração — Território\n\nA comunicação entre o Território e o DiscordSRV está funcionando!",sender);
    }
    private void send(String message,CommandSender sender) {
        if(!plugin.options().discord())return;
        String actor=sender==null?null:sender.getName();
        CompletableFuture<Void> result;
        try {result=transport.send(plugin.options().channel(),message).orTimeout(30,TimeUnit.SECONDS);}
        catch(RuntimeException e){result=CompletableFuture.failedFuture(e);}
        result.whenComplete((nothing,error)->{
            if(error!=null)plugin.activity("DISCORD","Falha no envio pelo DiscordSRV ("+root(error).getClass().getSimpleName()+"). Verifique conexão, canal mapeado e permissões do bot.");
            else if(actor!=null)plugin.activity("ADMIN",actor+" confirmou teste DiscordSRV");
            if(sender!=null&&plugin.isEnabled())plugin.getServer().getScheduler().runTask(plugin,()->sender.sendMessage(Component.text(error==null?
                "[Território] Teste enviado e confirmado pelo Discord.":"[Território] Falha no teste: verifique DiscordSRV conectado, canal de chat mapeado e permissão de envio do bot. Consulte os logs.")));
        });
    }
    private static Throwable root(Throwable error) {while(error.getCause()!=null)error=error.getCause();return error;}
    private static String label(Domain.Group group) {return ("["+group.tag+"] "+group.name).replace("@","@\u200b");}

    /** Signatures verified against DiscordSRV v1.30.5, shaded JDA 4.4.1. */
    static final class DiscordSrvTransport implements Transport {
        private final TerritorioPlugin plugin;
        DiscordSrvTransport(TerritorioPlugin plugin) {this.plugin=plugin;}
        @Override public CompletableFuture<Void> send(String logicalChannel,String text) {
            CompletableFuture<Void> done=new CompletableFuture<>();
            try {
                Plugin discord=plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
                if(discord==null||!discord.isEnabled())throw new IllegalStateException("DiscordSRV indisponível");
                Class<?> api=Class.forName("github.scarsz.discordsrv.DiscordSRV",true,discord.getClass().getClassLoader());
                Object jda=api.getMethod("getJda").invoke(discord);
                if(jda==null)throw new IllegalStateException("DiscordSRV não conectado");
                String channel=logicalChannel.isBlank()?(String)api.getMethod("getMainChatChannel").invoke(discord):logicalChannel;
                if(channel==null||channel.isBlank())throw new IllegalStateException("Canal de chat não mapeado");
                Object destination=api.getMethod("getDestinationTextChannelForGameChannelName",String.class).invoke(discord,channel);
                if(destination==null)throw new IllegalStateException("Canal de chat indisponível");
                queue(discord.getClass().getClassLoader(),destination,text,done);
            }catch(ReflectiveOperationException|RuntimeException|LinkageError e){done.completeExceptionally(e);}
            return done;
        }
        static void queue(ClassLoader loader,Object destination,String text,CompletableFuture<Void> done) throws ReflectiveOperationException {
            // Invoke public interfaces, not JDA's implementation classes. No setAccessible/private API.
            String base="github.scarsz.discordsrv.dependencies.jda.api.";
            Class<?> channel=loader.loadClass(base+"entities.MessageChannel");
            Class<?> action=loader.loadClass(base+"requests.restaction.MessageAction");
            Class<?> rest=loader.loadClass(base+"requests.RestAction");
            Object request=channel.getMethod("sendMessage",CharSequence.class).invoke(destination,text);
            request=action.getMethod("allowedMentions",Collection.class).invoke(request,List.of());
            Consumer<Object> success=message->done.complete(null);
            Consumer<Throwable> failure=done::completeExceptionally;
            rest.getMethod("queue",Consumer.class,Consumer.class).invoke(request,success,failure);
        }
    }
}
