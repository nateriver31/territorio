package dev.nateriver.territorio;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Ordered single-writer, immutable JSON snapshots and atomic replacement. */
public final class StateStore implements AutoCloseable {
    static final Gson JSON=new GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create();
    private final Path file;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Territorio-storage");t.setDaemon(true);return t;});
    private final AtomicReference<byte[]> pending=new AtomicReference<>();
    private final AtomicBoolean scheduled=new AtomicBoolean();
    private final Consumer<Throwable> errors;
    private volatile Throwable failure;
    public StateStore(Path directory,Consumer<Throwable> errors) throws IOException {Files.createDirectories(directory);file=directory.resolve("state.json");this.errors=errors;}
    public Domain.State load() throws IOException {
        if(!Files.exists(file))return new Domain.State();
        try {JsonObject raw=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for(String required:List.of("schemaVersion","groups","claims","wars","raids","names","joinCooldown","victimCooldown","raiderCooldown","invites","allianceInvites","trusted"))if(!raw.has(required)||raw.get(required).isJsonNull())throw new IllegalArgumentException("Campo obrigatório ausente: "+required);
            int version=raw.get("schemaVersion").getAsBigDecimal().intValueExact();
            if(version!=2&&version!=3)throw new IllegalArgumentException("Versão de dados desconhecida");
            if(version==3&&!raw.has("bonusMaxPerMember"))throw new IllegalArgumentException("Capacidade de bônus ausente");
            exactBonusNumber(raw,"bonusMaxPerMember",true);
            for(var group:raw.getAsJsonObject("groups").entrySet()) {
                JsonObject value=group.getValue().getAsJsonObject();
                if(version==3&&(!value.has("bonusEarned")||value.get("bonusEarned").isJsonNull()||!value.has("bonusSequence")||value.get("bonusSequence").isJsonNull()))
                    throw new IllegalArgumentException("Dados de bônus ausentes em grupo existente");
                exactBonusNumber(value,"bonusEarned",true);exactBonusNumber(value,"bonusSequence",false);
            }
            if(raw.get("claims").isJsonArray())for(JsonElement entry:raw.getAsJsonArray("claims"))
                exactBonusNumber(entry.getAsJsonArray().get(1).getAsJsonObject(),"bonusOrder",false);
            Domain.State state=JSON.fromJson(raw,Domain.State.class);
            if(version==2) {
                // A schema-2 file cannot already contain bonus land or balances: never reinterpret it.
                if(state.groups.values().stream().anyMatch(g->g.bonusEarned!=0||g.bonusSequence!=0)
                        ||state.claims.values().stream().anyMatch(c->c.kind==Domain.Kind.BONUS||c.bonusOrder!=0))
                    throw new IllegalArgumentException("Dados de bônus inesperados no esquema antigo");
                state.schemaVersion=3;state.bonusMaxPerMember=6;validate(state);
                // Only a verified in-memory migration is returned. Original is untouched until save.
                // Preserve the very first pre-migration copy; do not replace it on retries.
                Path backup=file.resolveSibling("state.json.pre-v3.bak");
                if(Files.exists(backup)) {
                    if(!Files.isRegularFile(backup)||Files.isSymbolicLink(backup))throw new IOException("Backup pre-v3 inválido; estado original preservado.");
                } else Files.copy(file,backup);
            } else validate(state);
            return state;}
        catch(RuntimeException e) {throw new IOException("state.json inválido: não será sobrescrito. Restaure backup manualmente.",e);}
    }
    private static void exactBonusNumber(JsonObject object,String key,boolean integer) {
        if(!object.has(key))return;
        JsonElement value=object.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Número de bônus inválido: "+key);
        if(integer)value.getAsBigDecimal().intValueExact();else value.getAsBigDecimal().longValueExact();
    }
    public static void validate(Domain.State s) {
        Objects.requireNonNull(s);if(s.schemaVersion!=3)throw new IllegalArgumentException("Versão de dados desconhecida");
        if(s.bonusMaxPerMember<1)throw new IllegalArgumentException("Capacidade de bônus inválida");
        Objects.requireNonNull(s.groups);Objects.requireNonNull(s.claims);Objects.requireNonNull(s.wars);Objects.requireNonNull(s.raids);Objects.requireNonNull(s.names);Objects.requireNonNull(s.joinCooldown);Objects.requireNonNull(s.victimCooldown);Objects.requireNonNull(s.raiderCooldown);Objects.requireNonNull(s.invites);Objects.requireNonNull(s.allianceInvites);Objects.requireNonNull(s.trusted);
        Set<UUID> players=new HashSet<>();Set<String> names=new HashSet<>(),tags=new HashSet<>();
        s.groups.forEach((id,g)->{
            if(!id.equals(g.id)||g.members==null||g.allies==null||g.name==null||g.tag==null||g.leader==null||!g.members.containsKey(g.leader)||g.members.get(g.leader).role!=Domain.Role.LEADER)throw new IllegalArgumentException("Grupo inconsistente");
            if(!names.add(java.text.Normalizer.normalize(g.name,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))||!tags.add(java.text.Normalizer.normalize(g.tag,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Identidade duplicada");
            if(g.members.values().stream().filter(m->m.role==Domain.Role.LEADER).count()!=1||g.members.values().stream().filter(m->m.role==Domain.Role.COLEADER).count()>1)throw new IllegalArgumentException("Cargos inconsistentes");
            if(g.bonusEarned<0||g.bonusEarned>Math.min(Integer.MAX_VALUE,(long)g.members.size()*s.bonusMaxPerMember)||g.bonusSequence<0)
                throw new IllegalArgumentException("Saldo de bônus inconsistente");
            g.members.forEach((p,m)->{if(!players.add(p)||!p.equals(m.player)||m.role==null)throw new IllegalArgumentException("Membro inconsistente");});
            g.allies.forEach((target,permissions)->{if(!s.groups.containsKey(target)||permissions==null)throw new IllegalArgumentException("Aliança inconsistente");});
        });
        Map<UUID,Integer> personal=new HashMap<>();
        Map<UUID,Integer> bonuses=new HashMap<>();Map<UUID,Set<Long>> orders=new HashMap<>();
        s.claims.forEach((at,c)->{
            if(at==null||at.world()==null||c==null||c.owner==null||c.contributor==null||c.kind==null||(Domain.collective(c.kind)&&!s.groups.containsKey(c.owner)))throw new IllegalArgumentException("Claim inconsistente");
            if(c.kind==Domain.Kind.PERSONAL&&personal.merge(c.owner,1,Integer::sum)>1)throw new IllegalArgumentException("Chunk pessoal duplicado");
            if(c.kind==Domain.Kind.BONUS) {
                Domain.Group group=s.groups.get(c.owner);
                if(c.bonusOrder<=0||c.bonusOrder>group.bonusSequence||!orders.computeIfAbsent(c.owner,k->new HashSet<>()).add(c.bonusOrder)
                        ||bonuses.merge(c.owner,1,Integer::sum)>group.bonusEarned)throw new IllegalArgumentException("Ordem ou utilização de bônus inconsistente");
            } else if(c.bonusOrder!=0)throw new IllegalArgumentException("Claim antigo com ordem de bônus indevida");
        });
        for(var cooldown:List.of(s.joinCooldown,s.victimCooldown,s.raiderCooldown))cooldown.forEach((p,t)->{if(p==null||t==null||t<0)throw new IllegalArgumentException("Cooldown inválido");});
        s.trusted.forEach((p,t)->{if(p==null||t==null||t.contains(null))throw new IllegalArgumentException("Permissão pessoal inválida");});
        s.invites.forEach((p,i)->{if(p==null||i==null||!s.groups.containsKey(i.group)||i.expires<0)throw new IllegalArgumentException("Convite inválido");});
        s.allianceInvites.forEach((p,i)->{if(p==null||i==null||!p.equals(i.to)||!s.groups.containsKey(i.from)||!s.groups.containsKey(i.to)||i.expires<0)throw new IllegalArgumentException("Convite de aliança inválido");});
        Set<UUID> warIds=new HashSet<>();Set<String> activeWars=new HashSet<>();
        s.wars.forEach(w->{if(w.id==null||w.attacker==null||w.defender==null||w.attacker.equals(w.defender)||(w.ended==0&&(!s.groups.containsKey(w.attacker)||!s.groups.containsKey(w.defender))))throw new IllegalArgumentException("Guerra inconsistente");
            if(!warIds.add(w.id)||w.started<0||w.ended<0||(w.peaceFrom!=null&&!w.peaceFrom.equals(w.attacker)&&!w.peaceFrom.equals(w.defender)))throw new IllegalArgumentException("Guerra duplicada ou paz inválida");
            String pair=w.attacker.compareTo(w.defender)<0?w.attacker+":"+w.defender:w.defender+":"+w.attacker;
            if(w.ended==0&&!activeWars.add(pair))throw new IllegalArgumentException("Guerra ativa duplicada");
        });
        s.raids.forEach(r->{if(r==null||r.invader==null||r.victim==null||r.result==null||r.started<0||r.ended<0||r.blocks<0)throw new IllegalArgumentException("Histórico de saque inválido");});
    }
    public void save(Domain.State state) {
        if(failure!=null)throw new IllegalStateException("Persistência indisponível; alterações bloqueadas",failure);
        validate(state);
        pending.set(JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        schedule();
    }
    public boolean healthy() {return failure==null;}
    private void schedule() {if(scheduled.compareAndSet(false,true))writer.execute(()->{
        try {byte[] bytes;while((bytes=pending.getAndSet(null))!=null)write(bytes);}
        catch(Throwable e){failure=e;errors.accept(e);}
        finally {scheduled.set(false);if(pending.get()!=null&&failure==null)schedule();}
    });}
    private void write(byte[] bytes) throws IOException {
        Path temp=file.resolveSibling("state.json.tmp");
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {ByteBuffer buf=ByteBuffer.wrap(bytes);while(buf.hasRemaining())channel.write(buf);channel.force(true);}
        if(Files.exists(file)) {
            Path backup=file.resolveSibling("state.json.bak.tmp");Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);
            Files.move(backup,file.resolveSibling("state.json.bak"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }
        Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    /** Audit append shares the ordered IO executor; never touches other plugin paths. */
    public void audit(String actor,String operation,String target,String before,String after) {
        Map<String,String> row=new LinkedHashMap<>();row.put("time",java.time.Instant.now().toString());row.put("actor",actor);row.put("operation",operation);row.put("target",target);row.put("before",before);row.put("after",after);
        String line=JSON.toJsonTree(row).toString()+"\n";
        writer.execute(()->{try{Files.writeString(file.resolveSibling("audit.jsonl"),line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException e){failure=e;errors.accept(e);}});
    }
    /** Completes only after queued snapshots are durable. Callers must return to the server thread. */
    public CompletableFuture<Void> checkpoint() {
        CompletableFuture<Void> result=new CompletableFuture<>();
        writer.execute(()->{
            try {
                if(failure!=null)throw new IOException("Falha de armazenamento",failure);
                byte[] bytes=pending.getAndSet(null);if(bytes!=null)write(bytes);
                result.complete(null);
            }catch(Throwable e){failure=e;errors.accept(e);result.completeExceptionally(e);}
        });
        return result;
    }
    public void flush() throws Exception {writer.submit(()->{byte[] bytes=pending.getAndSet(null);if(bytes!=null&&failure==null)try{write(bytes);}catch(IOException e){failure=e;errors.accept(e);}}).get(30,TimeUnit.SECONDS);if(failure!=null)throw new IOException("Falha de armazenamento",failure);}
    public void close() throws Exception {try{flush();}finally{writer.shutdown();}if(!writer.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Timeout ao salvar Territorio");}
}
