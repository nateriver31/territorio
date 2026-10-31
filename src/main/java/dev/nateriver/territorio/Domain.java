package dev.nateriver.territorio;

import java.text.Normalizer;
import java.util.*;
import java.util.function.*;

/** Main-thread domain model. No Bukkit objects or IO in rule evaluation. */
public final class Domain {
    public enum Role { LEADER, COLEADER, OFFICER, MEMBER }
    public enum Kind { SOLO, GROUP, PERSONAL }
    public enum Access { BUILD, CONTAINERS, MECHANISMS }
    public record Chunk(UUID world, int x, int z) {}
    public static final class Member {
        public UUID player; public Role role; public long joined, ranked;
        public Member(UUID p, Role r, long n) { player=p;role=r;joined=n;ranked=n; }
    }
    public static final class Group {
        public UUID id,leader; public String name,tag;
        public Map<UUID,Member> members=new LinkedHashMap<>();
        public Map<UUID,EnumSet<Access>> allies=new LinkedHashMap<>();
    }
    public static final class Claim {
        public Kind kind; public UUID owner, contributor;
        public Claim(Kind k,UUID o) {kind=k;owner=o;contributor=k==Kind.GROUP?null:o;}
    }
    public static final class War {
        public UUID id,attacker,defender,peaceFrom; public long started,ended; public String result,attackerLabel,defenderLabel;
    }
    public static final class RaidRecord {
        public UUID invader,victim; public long started,ended; public int blocks; public String result;
    }
    public static final class Invite {
        public UUID group; public long expires;
        public Invite(UUID g,long e) {group=g;expires=e;}
    }
    public static final class AllianceInvite {
        public UUID from,to; public long expires;
        public AllianceInvite(UUID f,UUID t,long e) {from=f;to=t;expires=e;}
    }
    public static final class State {
        public int schemaVersion=2;
        public Map<UUID,Group> groups=new LinkedHashMap<>();
        public Map<Chunk,Claim> claims=new LinkedHashMap<>();
        public Map<UUID,Long> joinCooldown=new HashMap<>(),victimCooldown=new HashMap<>(),raiderCooldown=new HashMap<>();
        public Map<UUID,String> names=new HashMap<>();
        public List<War> wars=new ArrayList<>(); public List<RaidRecord> raids=new ArrayList<>();
        public Map<UUID,Invite> invites=new HashMap<>();
        public Map<UUID,AllianceInvite> allianceInvites=new HashMap<>();
        public Map<UUID,Set<UUID>> trusted=new HashMap<>();
    }
    public State s=new State();
    public int contribution=9,soloLimit=9,nameMin=3,nameMax=32;
    public Runnable onChange=()->{};
    public Predicate<UUID> activeVictim=id->false;
    private final LongSupplier clock;
    private final Map<UUID,Group> membership=new HashMap<>();
    private final Map<UUID,List<War>> activeWars=new HashMap<>();
    public Domain() {this(System::currentTimeMillis);}
    public Domain(LongSupplier clock) {this.clock=clock;}
    public long now() {return clock.getAsLong();}
    public void changed() { reindex();onChange.run(); }
    public void reindex() {
        membership.clear();s.groups.values().forEach(g->g.members.keySet().forEach(p->membership.put(p,g)));
        activeWars.clear();for(War w:s.wars)if(w.ended==0){activeWars.computeIfAbsent(w.attacker,k->new ArrayList<>()).add(w);activeWars.computeIfAbsent(w.defender,k->new ArrayList<>()).add(w);}
    }
    public Group group(UUID player) {return membership.get(player);}
    private static String key(String v) {return Normalizer.normalize(v,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    public Group groupByName(String text) {return s.groups.values().stream().filter(g->key(g.name).equals(key(text))||key(g.tag).equals(key(text))||g.id.toString().equals(text)).findFirst().orElse(null);}
    public Claim claim(Chunk c) {return s.claims.get(c);}
    public boolean isLeader(UUID p) {Group g=group(p);return g!=null&&g.leader.equals(p);}
    public boolean mayManage(UUID p) {Group g=group(p);return g!=null&&(g.members.get(p).role==Role.LEADER||g.members.get(p).role==Role.COLEADER);}
    /** A contribution remains committed until its land is released, even after leaving. */
    public long committedElsewhere(UUID player,UUID groupId) {return s.claims.values().stream().filter(c->c.kind!=Kind.PERSONAL&&player.equals(c.contributor)&&!(c.kind==Kind.GROUP&&c.owner.equals(groupId))).count();}
    public long capacity(Group g) {return g.members.keySet().stream().mapToLong(p->Math.max(0,contribution-committedElsewhere(p,g.id))).sum();}
    private long committed(UUID player) {return s.claims.values().stream().filter(c->c.kind!=Kind.PERSONAL&&player.equals(c.contributor)).count();}
    public long count(Kind k,UUID owner) {return s.claims.values().stream().filter(c->c.kind==k&&c.owner.equals(owner)).count();}
    public boolean inWar(UUID g) {return !activeWars.getOrDefault(g,List.of()).isEmpty();}
    private void require(boolean ok,String error) {if(!ok)throw new IllegalArgumentException(error);}
    private Group needGroup(UUID p) {Group g=group(p);require(g!=null,"Você não está em um grupo.");return g;}
    private Group leader(UUID p) {Group g=needGroup(p);require(g.leader.equals(p),"Somente o líder pode fazer isso.");return g;}
    private void noWar(Group g) {require(!inWar(g.id),"Ação bloqueada durante a guerra.");}
    private void mayJoin(UUID p) {require(group(p)==null,"Você já está em um grupo.");require(s.joinCooldown.getOrDefault(p,0L)<=now(),"Aguarde o cooldown de saída de duas horas.");require(!activeVictim.test(p),"Você está sendo saqueado e não pode entrar ou criar um grupo.");}
    private void validateLabels(UUID except,String name,String tag) {
        require(name!=null&&tag!=null,"Informe nome e TAG.");
        require(name.length()>=nameMin&&name.length()<=nameMax&&name.equals(name.strip())&&name.matches("[\\p{L}\\p{N}]+(?:[ _-][\\p{L}\\p{N}]+)*"),"Nome inválido: use letras, números e espaços, dentro dos limites configurados.");
        require(tag.matches("[A-Za-z0-9]{2,5}"),"A TAG deve ter de 2 a 5 letras ou números, sem cores.");
        for(Group g:s.groups.values()) if(!g.id.equals(except)) {
            require(!key(g.name).equals(key(name)),"Nome de grupo já utilizado.");require(!key(g.tag).equals(key(tag)),"TAG já utilizada.");
        }
    }
    public Group create(UUID player,String name,String tag) {
        mayJoin(player);validateLabels(null,name,tag);require(count(Kind.SOLO,player)<=contribution,"Seus claims solo ultrapassam a contribuição coletiva configurada.");
        Group g=new Group();g.id=UUID.randomUUID();g.name=name;g.tag=tag.toUpperCase(Locale.ROOT);g.leader=player;
        g.members.put(player,new Member(player,Role.LEADER,now()));s.groups.put(g.id,g);
        migrate(player,g);changed();return g;
    }
    private void migrate(UUID p,Group g) {s.claims.values().forEach(c->{if(c.kind==Kind.SOLO&&c.owner.equals(p)){c.kind=Kind.GROUP;c.owner=g.id;}});}
    public void claim(UUID p,Chunk at,boolean personal) {
        require(!s.claims.containsKey(at),"Este chunk já está protegido.");Group g=group(p);
        if(personal) {require(g!=null,"O chunk pessoal extra requer grupo.");require(count(Kind.PERSONAL,p)<1,"Você já possui um chunk pessoal.");s.claims.put(at,new Claim(Kind.PERSONAL,p));}
        else if(g==null) {require(committed(p)<soloLimit,"Sua contribuição já está comprometida com claims solo ou de grupos anteriores.");s.claims.put(at,new Claim(Kind.SOLO,p));}
        else {require(mayManage(p),"Somente líder ou colíder administra claims coletivos.");noWar(g);require(count(Kind.GROUP,g.id)<capacity(g),"Grupo na capacidade máxima ou acima dela; remova claims ou regularize os membros.");UUID backer=g.members.values().stream().sorted(Comparator.comparingLong((Member m)->m.joined).thenComparing(m->m.player.toString())).map(m->m.player).filter(id->committed(id)<contribution).findFirst().orElseThrow(()->new IllegalArgumentException("Todas as contribuições estão comprometidas com claims existentes."));
            Claim c=new Claim(Kind.GROUP,g.id);c.contributor=backer;s.claims.put(at,c);}
        changed();
    }
    public void unclaim(UUID p,Chunk at) {
        Claim c=claim(at);require(c!=null,"Chunk sem proteção.");
        if(c.kind==Kind.GROUP) {Group g=needGroup(p);require(g.id.equals(c.owner)&&mayManage(p),"Sem permissão para remover este claim.");noWar(g);}
        else {require(c.owner.equals(p),"Este claim não é seu.");require(!activeVictim.test(p),"Não é possível remover claims durante o saque.");}
        s.claims.remove(at);changed();
    }
    public void invite(UUID actor,UUID target) {
        Group g=needGroup(actor);require(g.members.get(actor).role!=Role.MEMBER,"Seu cargo não pode convidar.");require(group(target)==null,"Jogador já pertence a um grupo.");require(s.joinCooldown.getOrDefault(target,0L)<=now(),"Jogador em cooldown de saída.");s.invites.put(target,new Invite(g.id,now()+300_000));changed();
    }
    public void accept(UUID p) {
        mayJoin(p);Invite i=s.invites.get(p);require(i!=null&&i.expires>now(),"Convite ausente ou expirado.");Group g=s.groups.get(i.group);require(g!=null,"O grupo não existe mais.");
        // No land imports during war. Existing over-capacity groups may gain real, available contributions.
        require(!inWar(g.id),"Não é possível entrar em um grupo durante uma guerra.");
        long outside=s.claims.values().stream().filter(c->c.kind==Kind.GROUP&&!c.owner.equals(g.id)&&p.equals(c.contributor)).count();
        require(count(Kind.SOLO,p)+outside<=contribution,"Os claims importados ultrapassam sua contribuição; parte dela está comprometida em outro grupo.");
        g.members.put(p,new Member(p,Role.MEMBER,now()));migrate(p,g);s.invites.remove(p);changed();
    }
    public void refuse(UUID p) {require(s.invites.remove(p)!=null,"Nenhum convite pendente.");changed();}
    public void leave(UUID p) {
        Group g=needGroup(p);noWar(g);g.members.remove(p);s.joinCooldown.put(p,now()+7_200_000);
        if(g.members.isEmpty())removeGroup(g);else if(g.leader.equals(p))succeed(g);
        changed();
    }
    private void succeed(Group g) {
        Member next=g.members.values().stream().min(Comparator.comparingInt((Member m)->m.role.ordinal()).thenComparingLong(m->m.role==Role.MEMBER?m.joined:m.ranked).thenComparing(m->m.player.toString())).orElseThrow();
        next.role=Role.LEADER;next.ranked=now();g.leader=next.player;
    }
    public void kick(UUID actor,UUID target) {
        Group g=needGroup(actor);noWar(g);require(mayManage(actor),"Seu cargo não pode expulsar.");Member m=g.members.get(target);require(m!=null&&!actor.equals(target),"Membro inválido.");require(m.role==Role.MEMBER,"Rebaixe o cargo superior antes de expulsar.");g.members.remove(target);changed();
    }
    public void rank(UUID actor,UUID target,Role role) {
        Group g=needGroup(actor);Member t=g.members.get(target);require(t!=null&&!actor.equals(target),"Membro inválido.");require(mayManage(actor),"Seu cargo não administra cargos.");
        if(role==Role.LEADER) {
            require(g.leader.equals(actor),"Somente o líder pode transferir a liderança.");noWar(g);
            g.members.get(actor).role=Role.MEMBER;g.members.get(actor).ranked=now();t.role=Role.LEADER;t.ranked=now();g.leader=target;changed();return;
        }
        require(t.role!=Role.LEADER,"O líder não pode ser rebaixado.");
        if(role==Role.COLEADER||t.role==Role.COLEADER)require(g.leader.equals(actor),"Somente o líder nomeia ou remove o colíder.");
        require(role!=Role.COLEADER||g.members.values().stream().noneMatch(m->m.role==Role.COLEADER&&!m.player.equals(target)),"O grupo já tem colíder.");
        t.role=role;t.ranked=now();changed();
    }
    public void dissolve(UUID actor) {Group g=leader(actor);noWar(g);removeGroup(g);changed();}
    private void removeGroup(Group g) {
        s.claims.entrySet().removeIf(e->e.getValue().kind==Kind.GROUP&&e.getValue().owner.equals(g.id));s.groups.remove(g.id);
        s.groups.values().forEach(other->other.allies.remove(g.id));s.invites.entrySet().removeIf(e->e.getValue().group.equals(g.id));s.allianceInvites.entrySet().removeIf(e->e.getValue().from.equals(g.id)||e.getValue().to.equals(g.id));
    }
    public void trust(UUID owner,UUID player,boolean allow) {require(!owner.equals(player),"O dono já possui acesso.");Set<UUID> t=s.trusted.computeIfAbsent(owner,k->new HashSet<>());if(allow)t.add(player);else t.remove(player);changed();}
    public boolean canAccess(UUID p,Claim c,Access access) {
        if(c==null)return true;
        if(c.kind!=Kind.GROUP)return c.owner.equals(p)||s.trusted.getOrDefault(c.owner,Set.of()).contains(p);
        Group owner=s.groups.get(c.owner);if(owner==null)return false;Group visitor=group(p);if(owner==visitor)return true;
        for(War w:activeWars.getOrDefault(owner.id,List.of())) {
            if(w.attacker.equals(owner.id))return true; // Declaring side vulnerable to everybody.
            if(w.defender.equals(owner.id)&&visitor!=null&&w.attacker.equals(visitor.id))return true;
        }
        return visitor!=null&&owner.allies.getOrDefault(visitor.id,EnumSet.noneOf(Access.class)).contains(access)&&!enemies(owner.id,visitor.id);
    }
    public boolean enemies(UUID a,UUID b) {return activeWars.getOrDefault(a,List.of()).stream().anyMatch(w->w.attacker.equals(b)||w.defender.equals(b));}
    public War declareWar(UUID actor,UUID other,Predicate<UUID> online) {
        Group a=leader(actor),b=s.groups.get(other);require(b!=null&&!a.id.equals(other),"Grupo alvo inválido.");require(!enemies(a.id,b.id),"Já existe guerra entre estes grupos.");
        require(online.test(a.leader)&&online.test(b.leader),"Os dois líderes precisam estar online para iniciar a guerra.");
        // Explicit fresh declaration, never a deferred auto-start. Remove old alliance permissions permanently.
        a.allies.remove(b.id);b.allies.remove(a.id);s.allianceInvites.entrySet().removeIf(e->(e.getValue().from.equals(a.id)&&e.getValue().to.equals(b.id))||(e.getValue().from.equals(b.id)&&e.getValue().to.equals(a.id)));
        War w=new War();w.id=UUID.randomUUID();w.attacker=a.id;w.defender=b.id;w.attackerLabel="["+a.tag+"] "+a.name;w.defenderLabel="["+b.tag+"] "+b.name;w.started=now();w.result="ATIVA";s.wars.add(w);changed();return w;
    }
    public War peace(UUID actor,UUID other,String action) {
        Group a=leader(actor);War w=s.wars.stream().filter(v->v.ended==0&&((v.attacker.equals(a.id)&&v.defender.equals(other))||(v.defender.equals(a.id)&&v.attacker.equals(other)))).findFirst().orElse(null);require(w!=null,"Nenhuma guerra ativa com esse grupo.");
        switch(action) {
            case "propor" -> {require(w.peaceFrom==null,"Já existe proposta pendente.");w.peaceFrom=a.id;}
            case "aceitar" -> {require(w.peaceFrom!=null&&!w.peaceFrom.equals(a.id),"Nenhuma proposta recebida.");w.ended=now();w.result="ACORDO_DE_PAZ";w.peaceFrom=null;}
            case "recusar" -> {require(w.peaceFrom!=null&&!w.peaceFrom.equals(a.id),"Nenhuma proposta recebida.");w.peaceFrom=null;}
            default -> throw new IllegalArgumentException("Use propor, aceitar ou recusar.");
        }changed();return w;
    }
    public void ally(UUID actor,UUID other,String action) {
        Group a=leader(actor),b=s.groups.get(other);require(b!=null&&!a.id.equals(other),"Grupo alvo inválido.");
        switch(action) {
            case "propor" -> {require(!enemies(a.id,b.id),"Grupos inimigos não podem ser aliados.");s.allianceInvites.put(b.id,new AllianceInvite(a.id,b.id,now()+300_000));}
            case "aceitar" -> {AllianceInvite i=s.allianceInvites.get(a.id);require(i!=null&&i.from.equals(b.id)&&i.expires>now()&&!enemies(a.id,b.id),"Proposta ausente ou expirada.");a.allies.put(b.id,EnumSet.noneOf(Access.class));b.allies.put(a.id,EnumSet.noneOf(Access.class));s.allianceInvites.remove(a.id);}
            case "recusar" -> {AllianceInvite i=s.allianceInvites.get(a.id);require(i!=null&&i.from.equals(b.id),"Nenhuma proposta desse grupo.");s.allianceInvites.remove(a.id);}
            case "remover" -> {a.allies.remove(b.id);b.allies.remove(a.id);}
            default -> throw new IllegalArgumentException("Use propor, aceitar, recusar ou remover.");
        }changed();
    }
    public void allyPermission(UUID actor,UUID other,Access access,boolean enabled) {Group g=leader(actor);require(g.allies.containsKey(other)&&!enemies(g.id,other),"Aliança inexistente ou suspensa.");if(enabled)g.allies.get(other).add(access);else g.allies.get(other).remove(access);changed();}
    public void adminRename(UUID id,String name,String tag) {Group g=s.groups.get(id);require(g!=null,"Grupo inexistente.");validateLabels(id,name,tag);g.name=name;g.tag=tag.toUpperCase(Locale.ROOT);changed();}
    public void adminLeader(UUID id,UUID player) {Group g=s.groups.get(id);require(g!=null&&g.members.containsKey(player),"O novo líder deve pertencer ao grupo.");g.members.get(g.leader).role=Role.MEMBER;g.members.get(g.leader).ranked=now();g.members.get(player).role=Role.LEADER;g.members.get(player).ranked=now();g.leader=player;changed();}
    public void adminUnclaim(Chunk c) {require(s.claims.remove(c)!=null,"Chunk não protegido.");changed();}
}
