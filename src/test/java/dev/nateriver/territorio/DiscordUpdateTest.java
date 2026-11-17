package dev.nateriver.territorio;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscordUpdateTest extends UpdateTestBase {
    final List<String> sent=new ArrayList<>(),channels=new ArrayList<>();
    void fakeTransport(){plugin.discord().transport((channel,text)->{sent.add(text);channels.add(channel);return CompletableFuture.completedFuture(null);});}
    @Test void announcementsOnlyFollowDurableDeclarationAndAcceptedPeaceNotProposalsOrRefusals() throws Exception {
        groups();fakeTransport();a.performCommand("t guerra VIK");assertTrue(sent.isEmpty());saved();assertEquals(1,sent.size());assertTrue(sent.getFirst().contains("GUERRA DECLARADA"));
        a.performCommand("t paz VIK");saved();assertEquals(1,sent.size());b.performCommand("t paz recusar");saved();assertEquals(1,sent.size());
        a.performCommand("t paz VIK");b.performCommand("t paz aceitar");assertEquals(1,sent.size());saved();assertEquals(2,sent.size());assertTrue(sent.getLast().contains("PAZ ESTABELECIDA"));
        b.performCommand("t paz aceitar");saved();assertEquals(2,sent.size());
        assertEquals(List.of("",""),channels,"Blank means use DiscordSRV's actual main chat mapping");
    }
    @Test void reloadAndRestartNeverReplayExistingWarsOrPeace() throws Exception {
        groups();fakeTransport();a.performCommand("t guerra VIK");saved();assertEquals(1,sent.size());plugin.reloadSafely();saved();assertEquals(1,sent.size());
        server.getPluginManager().disablePlugin(plugin);server.getPluginManager().enablePlugin(plugin);assertTrue(plugin.operational());fakeTransport();saved();assertEquals(1,sent.size());
    }
    @Test void disabledIntegrationOrAnnouncementFlagsDoNotPreventDiplomacy() throws Exception {
        groups();fakeTransport();configure("discordsrv.announce-war",false);a.performCommand("t guerra VIK");saved();assertTrue(sent.isEmpty());
        configure("discordsrv.announce-peace",false);a.performCommand("t paz VIK");b.performCommand("t paz aceitar");saved();assertTrue(sent.isEmpty());assertFalse(d.inWar(d.group(a.getUniqueId()).id));
    }
    @Test void missingDiscordSrvReportsTestFailureAndNeverBlocksADeclaredWar() throws Exception {
        groups();a.performCommand("t guerra VIK");saved();assertEquals(1,d.s.wars.size());
        a.addAttachment(plugin,"territorio.admin.discord",true);clearMessages();a.performCommand("t admin discord teste");server.getScheduler().performTicks(2);
        String response=String.join("\n",messages(a));assertTrue(response.contains("Falha no teste"));assertFalse(response.contains("confirmado pelo Discord"));
        plugin.activityLog().flush();assertTrue(logText().contains("DISCORD"));
    }
    @Test void testSuccessIsOnlyReportedAfterAcknowledgementAndNeverChangesState() throws Exception {
        groups();CompletableFuture<Void> acknowledgement=new CompletableFuture<>();
        plugin.discord().transport((channel,text)->{assertTrue(text.contains("Teste de integração"));assertFalse(text.contains("GUERRA DECLARADA"));return acknowledgement;});
        a.addAttachment(plugin,"territorio.admin.discord",true);String before=StateStore.JSON.toJson(d.s);clearMessages();
        a.performCommand("t admin discord teste");String pending=String.join("\n",messages(a));assertTrue(pending.contains("Enviando"));assertFalse(pending.contains("confirmado"));
        acknowledgement.complete(null);server.getScheduler().performTicks(2);assertTrue(String.join("\n",messages(a)).contains("confirmado pelo Discord"));
        assertEquals(before,StateStore.JSON.toJson(d.s));
    }
    @Test void testRequiresPermissionButConsoleMayUseItsOwnPermission() throws Exception {
        fakeTransport();a.performCommand("t admin discord teste");assertTrue(sent.isEmpty());
        var console=server.getConsoleSender();console.addAttachment(plugin,"territorio.admin.discord",true);
        server.dispatchCommand(console,"t admin discord teste");server.getScheduler().performTicks(2);assertEquals(1,sent.size());assertTrue(d.s.wars.isEmpty());
    }
    @Test void channelOptionUsesLogicalMappingAndErrorsAreLoggedWithoutLeakingExceptionContents() throws Exception {
        groups();configure("discordsrv.channel","chat-principal");
        plugin.discord().transport((channel,text)->{assertEquals("chat-principal",channel);return CompletableFuture.failedFuture(new IllegalStateException("SECRET_DO_NOT_LOG"));});
        a.performCommand("t guerra VIK");saved();assertEquals(1,d.s.wars.size());plugin.activityLog().flush();
        assertTrue(logText().contains("Falha no envio"));assertFalse(logText().contains("SECRET_DO_NOT_LOG"));
    }
    @Test void mentionSyntaxFromAnExistingGroupNameIsNeutralized() throws Exception {
        groups();fakeTransport();d.group(a.getUniqueId()).name="@everyone @here";
        a.performCommand("t guerra VIK");saved();assertEquals(1,sent.size());assertFalse(sent.getFirst().contains("@everyone"));assertFalse(sent.getFirst().contains("@here"));
    }
    String logText() throws Exception {
        Path dir=plugin.getDataFolder().toPath().resolve("logs");StringBuilder all=new StringBuilder();
        try(var paths=Files.list(dir)){for(Path path:paths.toList())all.append(Files.readString(path));}return all.toString();
    }

    public interface ApiChannel {Object sendMessage(CharSequence message);}
    public interface ApiAction {Object allowedMentions(Collection<?> allowed);}
    public interface ApiRest {void queue(Consumer<Object> success,Consumer<Throwable> failure);}
    public static final class Request implements ApiAction,ApiRest {
        Collection<?> allowed;Consumer<Object> success;Consumer<Throwable> failure;
        @Override public Object allowedMentions(Collection<?> allowed){this.allowed=allowed;return this;}
        @Override public void queue(Consumer<Object> success,Consumer<Throwable> failure){this.success=success;this.failure=failure;}
    }
    @Test void publicJdaAdapterDisablesMentionsAndWaitsForAsyncQueueCallback() throws Exception {
        Request request=new Request();ApiChannel channel=message->{assertEquals("test",message);return request;};
        ClassLoader api=new ClassLoader(){@Override public Class<?> loadClass(String name)throws ClassNotFoundException{
            if(name.endsWith("entities.MessageChannel"))return ApiChannel.class;
            if(name.endsWith("requests.restaction.MessageAction"))return ApiAction.class;
            if(name.endsWith("requests.RestAction"))return ApiRest.class;
            return super.loadClass(name);
        }};
        CompletableFuture<Void> future=new CompletableFuture<>();DiscordAnnouncements.DiscordSrvTransport.queue(api,channel,"test",future);
        assertTrue(request.allowed.isEmpty());assertFalse(future.isDone());request.success.accept(new Object());assertTrue(future.isDone());assertFalse(future.isCompletedExceptionally());
    }
}
