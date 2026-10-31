package dev.nateriver.territorio;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static dev.nateriver.territorio.Domain.*;

/** Player interface. All mutations execute on Paper's server thread. */
public final class Commands implements CommandExecutor, TabCompleter, Listener, AutoCloseable {
    private final TerritorioPlugin plugin;
    private final Domain d;
    private final RaidService raids;
    private final Set<UUID> viewing = new HashSet<>();
    private final Map<UUID, Territory> announced = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private BukkitTask particles;
    private TerritoryExpansion expansion;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(ZoneId.of("America/Sao_Paulo"));
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private record Territory(Kind kind, UUID owner) {}

    public Commands(TerritorioPlugin plugin, Domain d, RaidService raids) {
        this.plugin = plugin; this.d = d; this.raids = raids;
        for (Player p : Bukkit.getOnlinePlayers()) {
            d.s.names.put(p.getUniqueId(), p.getName());
            announced.put(p.getUniqueId(), territory(p.getLocation()));
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                TerritoryExpansion candidate = new TerritoryExpansion(d);
                if (candidate.register()) expansion = candidate;
                else plugin.getLogger().warning("PlaceholderAPI já possui expansão territorio; integração visual não foi registrada.");
            } catch (LinkageError | RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,"Integração PlaceholderAPI indisponível; Território continuará sem essa integração visual.",ex);
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] raw) {
        if (!plugin.operational()) { tell(sender,"§cTerritório em manutenção: armazenamento indisponível. Consulte a staff."); return true; }
        String[] args = normalize(cmd.getName(), raw);
        try {
            String canonical = cmd.getName().toLowerCase(Locale.ROOT);
            if (canonical.equals("roubar")) {
                Player p = player(sender); require(sender, "territorio.roubar");
                if (raw.length == 0) raids.start(p);
                else if (raw.length == 1 && raw[0].equalsIgnoreCase("status")) raids.status(p);
                else throw error("Use /roubar ou /roubar status.");
                return true;
            }
            if (canonical.equals("verchunk")) {
                Player p = player(sender); require(sender, "territorio.verchunk");
                toggleChunks(p); return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("admin")) { admin(sender, Arrays.copyOfRange(args, 1, args.length)); return true; }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { admin(sender, new String[]{"reload"}); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("guerra") && args[1].equalsIgnoreCase("encerrar")) {
                admin(sender, concat("encerrarguerra", Arrays.copyOfRange(args, 2, args.length))); return true;
            }
            Player p = player(sender); require(sender, "territorio.use");
            UUID who = p.getUniqueId();
            if (args.length == 0 || Set.of("ajuda", "help").contains(lower(args[0]))) { manual(p); return true; }
            String action = lower(args[0]);
            switch (action) {
                case "claim", "pessoal" -> {
                    require(p, "territorio.claim");
                    boolean personal = action.equals("pessoal") || (args.length > 1 && args[1].equalsIgnoreCase("pessoal"));
                    if (args.length > (action.equals("pessoal") ? 1 : 2) || (args.length==2 && !personal)) throw error("Use /t claim [pessoal].");
                    d.claim(who, chunk(p.getLocation()), personal);
                    tell(p, personal ? "§aChunk pessoal protegido." : "§aChunk protegido.");
                }
                case "unclaim", "desclaim" -> {
                    require(p, "territorio.claim"); d.unclaim(who, chunk(p.getLocation())); tell(p, "§aProteção do chunk removida. Nenhum bloco foi alterado.");
                }
                case "claims" -> claimInfo(p);
                case "info" -> {
                    if (args.length > 1) groupInfo(p, targetGroup(join(args,1)));
                    else { Group g = d.group(who); if (g != null) groupInfo(p,g); claimInfo(p); }
                }
                case "criar" -> {
                    require(p, "territorio.grupo"); need(args,3,"/t criar TAG Nome do grupo");
                    Group g = d.create(who,join(args,2),args[1]); tell(p,"§aGrupo criado: §6["+g.tag+"] §f"+g.name+".");
                }
                case "convidar" -> {
                    require(p,"territorio.grupo"); need(args,2,"/t convidar Jogador"); UUID target = targetPlayer(args[1]); d.invite(who,target);
                    tell(p,"§aConvite enviado. Expira em cinco minutos.");
                    privateMessage(target,"§eVocê recebeu um convite de §6["+myGroup(who).tag+"]§e. Use /t aceitar ou /t recusar em até cinco minutos.");
                }
                case "aceitar" -> { require(p,"territorio.grupo"); d.accept(who); tell(p,"§aConvite aceito. Você entrou em §6["+myGroup(who).tag+"]."); notifyGroup(myGroup(who).id,"§a"+p.getName()+" entrou no grupo."); }
                case "recusar" -> { require(p,"territorio.grupo"); d.refuse(who); tell(p,"§eConvite recusado."); }
                case "sair" -> { require(p,"territorio.grupo"); Group old=myGroup(who); d.leave(who); tell(p,"§eVocê saiu do grupo. Aguarde duas horas para criar ou entrar em outro."); notifyGroup(old.id,"§e"+p.getName()+" saiu do grupo."); }
                case "expulsar" -> {
                    require(p,"territorio.grupo"); need(args,2,"/t expulsar Jogador"); UUID target=targetPlayer(args[1]); d.kick(who,target); tell(p,"§aIntegrante expulso."); privateMessage(target,"§eVocê foi expulso do grupo. Não recebeu cooldown de entrada.");
                }
                case "cargo" -> {
                    require(p,"territorio.grupo"); need(args,3,"/t cargo Jogador membro|oficial|colider"); UUID target=targetPlayer(args[1]); Role role=parseRole(args[2]);
                    if (role==Role.LEADER) throw error("Use /t lider Jogador confirmar para transferir a liderança.");
                    d.rank(who,target,role); tell(p,"§aCargo atualizado."); privateMessage(target,"§eSeu cargo agora é "+roleName(role)+".");
                }
                case "lider" -> {
                    require(p,"territorio.grupo"); need(args,3,"/t lider Jogador confirmar"); if (!args[2].equalsIgnoreCase("confirmar")) throw error("Confirme com /t lider Jogador confirmar.");
                    UUID target=targetPlayer(args[1]); d.rank(who,target,Role.LEADER); tell(p,"§aLiderança transferida."); privateMessage(target,"§eVocê agora é líder do grupo.");
                }
                case "dissolver", "desfazer" -> {
                    require(p,"territorio.grupo"); need(args,2,"/t dissolver confirmar"); if (!args[1].equalsIgnoreCase("confirmar")) throw error("Use /t dissolver confirmar. Claims coletivos serão liberados; blocos e claims pessoais serão preservados.");
                    Group old=myGroup(who); List<UUID> members=List.copyOf(old.members.keySet()); d.dissolve(who);
                    for(UUID member:members) privateMessage(member,"§eO grupo foi dissolvido. Claims coletivos foram liberados; claims pessoais continuam protegidos.");
                }
                case "confiar", "desconfiar" -> {
                    require(p,"territorio.claim"); need(args,2,"/t "+action+" Jogador"); UUID target=targetPlayer(args[1]); d.trust(who,target,action.equals("confiar")); tell(p,"§aPermissão pessoal atualizada para "+name(target)+".");
                }
                case "lista", "grupos" -> {
                    require(p,"territorio.grupo"); int page=args.length>1?positive(args[1]):1;
                    List<Group> groups=d.s.groups.values().stream().sorted(Comparator.comparing(g->g.name.toLowerCase(Locale.ROOT))).toList();
                    tell(p,"§6Grupos — página "+page); for(Group g:page(groups,page)) tell(p,"§6["+g.tag+"] §f"+g.name+" §7("+g.members.size()+" membros)");
                }
                case "membros" -> { require(p,"territorio.grupo"); groupInfo(p,args.length>1?targetGroup(join(args,1)):myGroup(who)); }
                case "guerra" -> war(p,args);
                case "paz" -> peace(p,args);
                case "aliados", "alianca" -> allies(p,args);
                case "guerras" -> history(p,args,false);
                default -> throw error("Comando desconhecido. Use /t para abrir o manual.");
            }
        } catch (IllegalArgumentException e) { tell(sender,"§c"+e.getMessage()); }
        catch (Exception e) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Falha ao executar comando do Território",e); tell(sender,"§cNão foi possível concluir a operação. Consulte a staff."); }
        finally { if (expansion!=null) expansion.refresh(); }
        return true;
    }

    private void war(Player p,String[] a) {
        require(p,"territorio.grupo"); need(a,2,"/t guerra declarar TAG | /t guerra status");
        String act=lower(a[1]);
        if(act.equals("status")) { Group g=myGroup(p.getUniqueId()); List<War> wars=d.s.wars.stream().filter(w->w.ended==0 && involves(w,g.id)).toList(); if(wars.isEmpty())tell(p,"§eSeu grupo não está em guerra."); else for(War w:wars) warLine(p,w); return; }
        if(Set.of("paz","aceitarpaz","recusarpaz").contains(act)) { need(a,3,"/t guerra "+act+" TAG"); peace(p,new String[]{"paz",act.equals("paz")?"propor":act.equals("aceitarpaz")?"aceitar":"recusar",join(a,2)}); return; }
        if(!act.equals("declarar"))throw error("Use /t guerra declarar TAG ou /t guerra status.");
        need(a,3,"/t guerra declarar TAG"); Group target=targetGroup(join(a,2)); Group origin=myGroup(p.getUniqueId());
        d.declareWar(p.getUniqueId(),target.id,id->Bukkit.getPlayer(id)!=null);
        Bukkit.broadcast(LEGACY.deserialize("§c⚔ ["+origin.tag+"] declarou guerra a ["+target.tag+"]. Claims do atacante estão vulneráveis a todos."));
    }
    private void peace(Player p,String[] a) {
        require(p,"territorio.grupo"); need(a,3,"/t paz propor|aceitar|recusar TAG");
        String act=lower(a[1]); if(!Set.of("propor","aceitar","recusar").contains(act))throw error("Use /t paz propor|aceitar|recusar TAG.");
        Group target=targetGroup(join(a,2)), origin=myGroup(p.getUniqueId()); d.peace(p.getUniqueId(),target.id,act);
        if(act.equals("propor")) {
            notifyGroup(target.id,"§e["+origin.tag+"] propôs paz ao seu grupo. O líder pode usar /t paz aceitar "+origin.tag+" ou /t paz recusar "+origin.tag+".");
            notifyGroup(origin.id,"§eProposta de paz enviada a ["+target.tag+"].");
        } else {
            String text=act.equals("aceitar")?"§aA paz entre ["+origin.tag+"] e ["+target.tag+"] foi aceita. A guerra terminou.":"§e["+origin.tag+"] recusou a proposta de paz de ["+target.tag+"].";
            notifyGroup(target.id,text); notifyGroup(origin.id,text);
        }
    }
    private void allies(Player p,String[] a) {
        require(p,"territorio.grupo"); Group origin=myGroup(p.getUniqueId());
        String act=a.length<2?"lista":lower(a[1]);
        if(act.equals("lista")) {
            tell(p,"§6Alianças de ["+origin.tag+"] (permissões concedidas pelo seu grupo):");
            if(origin.allies.isEmpty())tell(p,"§7Nenhuma aliança.");
            origin.allies.forEach((id,permissions)->tell(p,"§e"+groupName(id)+" §7"+permissions.stream().map(Commands::accessName).collect(Collectors.joining(", ")))); return;
        }
        if(act.equals("permissao")) {
            need(a,5,"/t aliados permissao TAG portas|recipientes|construcao sim|nao"); Group target=targetGroup(a[2]); Access access=parseAccess(a[3]); boolean allow=parseBoolean(a[4]);
            d.allyPermission(p.getUniqueId(),target.id,access,allow); tell(p,"§aPermissão de aliança atualizada. O acesso concedido é somente aos claims coletivos do seu grupo."); return;
        }
        if(act.equals("convidar"))act="propor";
        if(!Set.of("propor","aceitar","recusar","remover").contains(act))throw error("Use /t aliados propor|aceitar|recusar|remover TAG ou /t aliados permissao TAG tipo sim|nao.");
        need(a,3,"/t aliados "+act+" TAG"); Group target=targetGroup(join(a,2)); d.ally(p.getUniqueId(),target.id,act);
        if(act.equals("propor"))notifyGroup(target.id,"§e["+origin.tag+"] propôs uma aliança. O líder decide com /t aliados aceitar "+origin.tag+" ou /t aliados recusar "+origin.tag+".");
        else notifyGroup(target.id,"§e["+origin.tag+"] atualizou a diplomacia: "+act+" aliança.");
        tell(p,"§aOperação de aliança concluída.");
    }

    private void admin(CommandSender sender,String[] input) {
        need(input,1,"/t admin inspecionar|nome|tag|lider|removerclaim|guerras|saques|reload|bypass|encerrarguerra");
        boolean force=input[input.length-1].equalsIgnoreCase("--forcar");
        String[] a=force?Arrays.copyOf(input,input.length-1):input;
        if(force) require(sender,"territorio.admin.force");
        if(a.length==0)throw error("Informe a operação administrativa antes de --forcar.");
        String op=lower(a[0]); String actor=sender instanceof Player p?p.getName()+"/"+p.getUniqueId():sender.getName();
        switch(op) {
            case "inspecionar" -> {
                require(sender,"territorio.admin.inspect");
                if(a.length>1)groupInfo(sender,targetGroup(join(a,1))); else claimInfo(player(sender));
            }
            case "nome", "tag" -> {
                require(sender,"territorio.admin.rename"); need(a,3,"/t admin "+op+" TAG Novo valor [--forcar]"); Group g=targetGroup(a[1]); warGuard(g,force);
                String before=g.name+" ["+g.tag+"]";
                d.adminRename(g.id,op.equals("nome")?join(a,2):g.name,op.equals("tag")?a[2]:g.tag);
                plugin.audit(actor,op+(force?" FORÇA":""),g.id.toString(),before,g.name+" ["+g.tag+"]"); tell(sender,"§aIdentidade do grupo corrigida e registrada.");
            }
            case "lider" -> {
                require(sender,"territorio.admin.leader"); need(a,3,"/t admin lider TAG Jogador [--forcar]"); Group g=targetGroup(a[1]); warGuard(g,force); UUID target=targetPlayer(a[2]); UUID old=g.leader;
                d.adminLeader(g.id,target); plugin.audit(actor,"lider"+(force?" FORÇA":""),g.id.toString(),old.toString(),target.toString()); tell(sender,"§aLiderança corrigida e registrada.");
            }
            case "removerclaim" -> {
                require(sender,"territorio.admin.unclaim"); Player p=player(sender); Chunk c=chunk(p.getLocation()); Claim claim=d.claim(c); if(claim==null)throw error("Este chunk não tem claim.");
                if(claim.kind==Kind.GROUP)warGuard(d.s.groups.get(claim.owner),force);
                String before=claim.kind+" "+claim.owner; d.adminUnclaim(c); plugin.audit(actor,"removerclaim"+(force?" FORÇA":""),c.toString(),before,"livre"); tell(sender,"§aClaim removido e registrado. Nenhum bloco foi alterado.");
            }
            case "guerras" -> { require(sender,"territorio.admin.wars"); history(sender,concat("guerras",Arrays.copyOfRange(a,1,a.length)),true); }
            case "saques" -> {
                require(sender,"territorio.admin.raids"); int pg=a.length>1?positive(a[1]):1;
                tell(sender,"§6Registros privados de saques — página "+pg);
                List<RaidRecord> records=new ArrayList<>(d.s.raids); Collections.reverse(records);
                for(RaidRecord r:page(records,pg)) tell(sender,"§7"+date(r.started)+" §f"+name(r.invader)+" → "+name(r.victim)+" §7blocos="+r.blocks+" resultado="+r.result+" fim="+date(r.ended));
            }
            case "reload" -> { require(sender,"territorio.admin.reload"); plugin.reloadSafely(); if(particles!=null){stopParticles();startParticles();} plugin.audit(actor,"reload","config.yml","configuração anterior","configuração recarregada"); tell(sender,"§aConfiguração recarregada com segurança."); }
            case "bypass" -> { require(sender,"territorio.admin.bypass"); Player p=player(sender); boolean old=plugin.bypassing(p.getUniqueId()); plugin.toggleBypass(p.getUniqueId()); boolean active=plugin.bypassing(p.getUniqueId()); plugin.audit(actor,"bypass",p.getUniqueId().toString(),Boolean.toString(old),Boolean.toString(active)); tell(p,active?"§cBypass de proteção ATIVO. Ativação registrada; use /t admin bypass para desativar.":"§aBypass desativado."); }
            case "encerrarguerra" -> {
                require(sender,"territorio.admin.wars"); require(sender,"territorio.admin.force"); need(a,3,"/t admin encerrarguerra TAG1 TAG2"); Group first=targetGroup(a[1]),second=targetGroup(a[2]);
                War w=d.s.wars.stream().filter(x->x.ended==0 && involves(x,first.id)&&involves(x,second.id)).findFirst().orElseThrow(()->error("Não há guerra ativa entre estes grupos."));
                w.ended=d.now(); w.result="INTERVENCAO_ADMINISTRATIVA"; w.peaceFrom=null; d.changed();
                plugin.audit(actor,"encerrarguerra FORÇA",w.id.toString(),"ATIVA","INTERVENCAO_ADMINISTRATIVA"); notifyGroup(first.id,"§eA staff encerrou a guerra com ["+second.tag+"]."); notifyGroup(second.id,"§eA staff encerrou a guerra com ["+first.tag+"]."); tell(sender,"§aGuerra encerrada e registrada.");
            }
            default -> throw error("Ferramenta administrativa desconhecida.");
        }
    }
    private void warGuard(Group g,boolean force) { if(g!=null&&d.inWar(g.id)&&!force)throw error("Grupo em guerra. Intervenção excepcional exige --forcar e territorio.admin.force."); }
    private void groupInfo(CommandSender s,Group g) {
        tell(s,"§6["+g.tag+"] §f"+g.name+" §7— líder: "+name(g.leader));
        long used=d.count(Kind.GROUP,g.id); tell(s,"§7Claims coletivos: "+used+"/"+d.capacity(g)+(used>d.capacity(g)?" (acima do limite; novas claims bloqueadas)":""));
        for(Member m:g.members.values().stream().sorted(Comparator.comparing((Member m)->m.role.ordinal()).thenComparing(m->name(m.player))).toList())tell(s,"§e"+name(m.player)+" §7— "+roleName(m.role));
    }
    private void claimInfo(Player p) {
        require(p,"territorio.claim"); Claim c=d.claim(chunk(p.getLocation()));
        tell(p,c==null?"§7Chunk atual: livre.":"§6Chunk atual: "+c.kind+" — "+ownerLabel(c));
        tell(p,"§7Seus claims solo: "+d.count(Kind.SOLO,p.getUniqueId())+"/"+d.soloLimit+"; pessoal: "+d.count(Kind.PERSONAL,p.getUniqueId())+"/1.");
        Set<UUID> trusted=d.s.trusted.getOrDefault(p.getUniqueId(),Set.of());
        if(!trusted.isEmpty())tell(p,"§7Pessoas de confiança: "+trusted.stream().map(this::name).sorted().collect(Collectors.joining(", ")));
    }
    private void history(CommandSender p,String[] a,boolean admin) {
        int pg=1; UUID group=null;
        if(a.length>1){try{pg=positive(a[1]);}catch(IllegalArgumentException ex){group=targetGroup(a[1]).id; if(a.length>2)pg=positive(a[2]);}}
        final UUID filter=group;
        List<War> history=d.s.wars.stream().filter(w->filter==null||involves(w,filter)).sorted(Comparator.comparingLong((War w)->w.started).reversed()).toList();
        tell(p,"§6Histórico de guerras — página "+pg); for(War w:page(history,pg))warLine(p,w);
    }
    private void warLine(CommandSender p,War w) {
        tell(p,"§e"+(w.attackerLabel==null?groupName(w.attacker):w.attackerLabel)+" → "+(w.defenderLabel==null?groupName(w.defender):w.defenderLabel)+" §7início: "+date(w.started)+"; fim: "+date(w.ended)+"; "+(w.ended==0?"ATIVA":w.result));
    }

    private void manual(Player p) {
        List<Component> pages=List.of(
            pageText("Território\nSurvival+\n\nProteja chunks, forme grupos e participe de guerras.\n\nO PvP continua normal em todo o servidor.\n\n/t abre este manual."),
            pageText("Claims\n\n/t claim\nProtege o chunk atual.\n/t unclaim\nRemove a proteção.\n/t claims\nConsulta as claims.\n\nSolo: até 9 chunks.\n/verchunk mostra os cantos do chunk."),
            pageText("Grupo e chunk pessoal\n\nCada membro contribui com 9 chunks coletivos.\n\n/t claim pessoal\nProtege 1 chunk extra privado.\n\n/t confiar Jogador\n/t desconfiar Jogador\nAcesso às suas claims individuais."),
            pageText("Criar e entrar\n\n/t criar TAG Nome\nTAG: 2 a 5 caracteres.\nNome e TAG são fixos.\n\n/t convidar Jogador\n/t aceitar\n/t recusar\nConvites duram 5 min.\n\n/t info e /t lista"),
            pageText("Cargos\n\nLíder: diplomacia e gestão.\nColíder: gestão, só um.\nOficial: pode convidar.\nMembro: usa as claims.\n\nTodos constroem nas claims coletivas.\n\n/t cargo Jogador cargo"),
            pageText("Saída e limites\n\n/t sair\nEspera de 2h para entrar ou criar outro grupo.\n\n/t expulsar Jogador\nSem espera para expulso.\n\nExcesso de claims bloqueia novas claims. Não apaga blocos ou libera terrenos."),
            pageText("Liderança\n\n/t lider Jogador confirmar\nTransfere liderança.\n\nAo sair, assume colíder, oficial ou membro mais antigo.\n\n/t dissolver confirmar\nLibera claims coletivos. Preserva claims pessoais e os blocos."),
            pageText("Alianças\n\n/t aliados propor TAG\n/t aliados aceitar TAG\n/t aliados recusar TAG\n/t aliados remover TAG\n\nSomente líderes decidem. Os acessos começam desativados."),
            pageText("Acesso de aliados\n\n/t aliados permissao TAG portas sim\nTipos: portas, recipientes, construcao.\nUse nao para revogar.\n\nConstrução vale para colocar e quebrar. Aliança não dá administração."),
            pageText("Guerras\n\n/t guerra declarar TAG\nLíderes dos dois grupos precisam estar online.\n\nClaims do atacante ficam abertos a todos. Defensor é vulnerável aos atacantes.\n\nSem saída, expulsão ou retirada de claims na guerra."),
            pageText("Paz e histórico\n\n/t paz propor TAG\n/t paz aceitar TAG\n/t paz recusar TAG\n\nSomente líderes decidem.\n\n/t guerra status\n/t guerras [página]\nMostra início, fim e resultado."),
            pageText("Saques de solo\n\nEntre no claim e use /roubar. Dono deve estar online.\n\nDura 5 minutos, todos os claims solo do dono.\nUm invasor, até 30 blocos, mineração mais lenta.\n\n/roubar status"),
            pageText("Durante o saque\n\nPode abrir baús e barris e retirar itens sem limite.\n\nNão pode destruir recipientes nem abrir portas pela permissão de saque.\n\nApós 30 blocos, ainda pode abrir os recipientes até o fim."),
            pageText("Defesa e cooldowns\n\nInvasor morto por jogador: defesa, identidade revelada e 24h de proteção.\n\nNormal: dono 12h, invasor 2h.\n\nDesconexão do invasor encerra. Dono offline: até 60s dentro do prazo original."),
            pageText("Comandos rápidos\n\n/t info\n/t claim\n/t claim pessoal\n/t convidar Jogador\n/t cargo Jogador cargo\n/t guerra status\n/t aliados lista\n/verchunk\n/roubar status"));
        p.openBook(Book.book(Component.text("Manual do Território"),Component.text("Survival+"),pages));
    }
    private static Component pageText(String text){ return Component.text(text); }

    private void toggleChunks(Player p) {
        if(viewing.remove(p.getUniqueId())) { tell(p,"§7Visualização de chunks desativada."); if(viewing.isEmpty())stopParticles(); return; }
        viewing.add(p.getUniqueId()); tell(p,"§aVisualização de chunks ativada. Use /verchunk para desativar.");
        if(particles==null)startParticles();
    }
    private void startParticles() {
        long interval=Math.max(5,Math.min(200,plugin.settings().getLong("visual.interval-ticks",20)));
        particles=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            Particle particle;
            try { particle=Particle.valueOf(plugin.settings().getString("visual.particle","HAPPY_VILLAGER").toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException ex){ particle=Particle.HAPPY_VILLAGER; }
            // Data-bearing particles require particle-specific data and are not accepted here.
            if(particle.getDataType()!=Void.class)particle=Particle.HAPPY_VILLAGER;
            int count=Math.max(1,Math.min(10,plugin.settings().getInt("visual.particles-per-corner",2)));
            for(UUID id:List.copyOf(viewing)) {
                Player p=Bukkit.getPlayer(id); if(p==null){viewing.remove(id);continue;}
                Location l=p.getLocation(); int x=l.getBlockX()>>4, z=l.getBlockZ()>>4;
                for(int dx:new int[]{0,16}) for(int dz:new int[]{0,16})p.spawnParticle(particle,new Location(l.getWorld(),(x<<4)+dx+0.05,l.getY()+0.3,(z<<4)+dz+0.05),count,0.05,0.1,0.05,0);
            }
            if(viewing.isEmpty())stopParticles();
        },0,interval);
    }
    private void stopParticles(){ if(particles!=null){particles.cancel();particles=null;} }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if(e.getTo()==null)return;
        if(!chunk(e.getFrom()).equals(chunk(e.getTo())))queueNotice(e.getPlayer(),false);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent e){ queueNotice(e.getPlayer(),true); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent e){ queueNotice(e.getPlayer(),true); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        if(!plugin.operational())return;
        Player p=e.getPlayer(); d.s.names.put(p.getUniqueId(),p.getName()); d.changed(); queueNotice(p,false);
        if(expansion!=null)expansion.refresh();
        Group g=d.group(p.getUniqueId());
        if(g!=null) {
            for(War w:d.s.wars)if(w.ended==0&&involves(w,g.id)&&w.peaceFrom!=null&&!w.peaceFrom.equals(g.id)) {
                privateMessage(p.getUniqueId(),"§eHá uma proposta de paz de "+groupName(w.peaceFrom)+". O líder decide com /t paz aceitar TAG ou /t paz recusar TAG.");
            }
        }
        if(g!=null && g.leader.equals(p.getUniqueId())) {
            AllianceInvite proposal=d.s.allianceInvites.get(g.id);
            if(proposal!=null && proposal.expires>d.now()) tell(p,"§eHá uma proposta de aliança de "+groupName(proposal.from)+". Use /t aliados aceitar TAG ou /t aliados recusar TAG.");
        }
        // Invitations are persisted; no notification is lost when the recipient logs in later.
        if(d.s.invites.containsKey(p.getUniqueId()) && d.s.invites.get(p.getUniqueId()).expires>d.now())tell(p,"§eVocê possui um convite de grupo. Use /t aceitar ou /t recusar; convites vencem em cinco minutos.");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId(); if(plugin.bypassing(id))plugin.toggleBypass(id); viewing.remove(id); announced.remove(id); BukkitTask task=pending.remove(id);if(task!=null)task.cancel();
        if(viewing.isEmpty())stopParticles();
    }
    private void queueNotice(Player p,boolean immediate) {
        UUID id=p.getUniqueId(); BukkitTask previous=pending.remove(id); if(previous!=null)previous.cancel();
        long delay=immediate?1:Math.max(1,Math.min(100,plugin.settings().getLong("notices.debounce-ticks",20)));
        pending.put(id,Bukkit.getScheduler().runTaskLater(plugin,()->{
            pending.remove(id); if(!p.isOnline())return; Territory current=territory(p.getLocation()),last=announced.get(id);
            if(Objects.equals(current,last))return;
            if(last!=null)tell(p,"§c< §fVocê saiu do território de "+territoryLabel(last)+"§f.");
            if(current!=null)tell(p,"§a> §fVocê entrou no território de "+territoryLabel(current)+"§f.");
            if(current==null)announced.remove(id);else announced.put(id,current);
        },delay));
    }
    private Territory territory(Location l){ Claim c=d.claim(chunk(l)); return c==null?null:new Territory(c.kind,c.owner); }
    private String territoryLabel(Territory t){ Group g=t.kind==Kind.GROUP?d.s.groups.get(t.owner):null; return t.kind==Kind.GROUP?"§6["+(g==null?"grupo removido":g.tag)+"]":"§6"+name(t.owner); }
    private String ownerLabel(Claim c){return c.kind==Kind.GROUP?groupName(c.owner):name(c.owner);}
    private String groupName(UUID id){Group g=d.s.groups.get(id);return g==null?"[grupo removido "+id.toString().substring(0,8)+"]":"["+g.tag+"] "+g.name;}
    private Group myGroup(UUID id){Group g=d.group(id);if(g==null)throw error("Você não pertence a um grupo.");return g;}
    private Group targetGroup(String input){Group g=d.groupByName(input);if(g==null)throw error("Grupo não encontrado. Use o nome completo ou a TAG.");return g;}
    private UUID targetPlayer(String input){
        Player online=Bukkit.getPlayerExact(input);if(online!=null)return online.getUniqueId();
        return d.s.names.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(input)).map(Map.Entry::getKey).findFirst().orElseThrow(()->error("Jogador desconhecido. Informe um jogador que já entrou no servidor."));
    }
    private String name(UUID id){ return d.s.names.getOrDefault(id,id.toString().substring(0,8)); }
    private void notifyGroup(UUID id,String text){Group g=d.s.groups.get(id);if(g!=null)for(UUID p:g.members.keySet())privateMessage(p,text);}
    private void privateMessage(UUID id,String text){Player p=Bukkit.getPlayer(id);if(p!=null)tell(p,text);}
    private static void tell(CommandSender to,String text){to.sendMessage(LEGACY.deserialize(text));}
    private static Player player(CommandSender s){if(s instanceof Player p)return p;throw error("Este comando precisa ser usado por um jogador.");}
    private static void require(CommandSender s,String node){if(!s.hasPermission(node))throw error("Você não tem permissão para esta ação.");}
    private static IllegalArgumentException error(String text){return new IllegalArgumentException(text);}
    private static String lower(String s){return s.toLowerCase(Locale.ROOT);}
    private static void need(String[] a,int n,String usage){if(a.length<n)throw error("Use "+usage+".");}
    private static String join(String[] a,int from){return String.join(" ",Arrays.copyOfRange(a,from,a.length));}
    private static int positive(String text){try{int n=Integer.parseInt(text);if(n<1)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw error("Informe uma página maior que zero.");}}
    private static boolean parseBoolean(String text){return switch(lower(text)){case "sim","true","on"->true;case "nao","não","false","off"->false;default->throw error("Use sim ou nao.");};}
    private static Role parseRole(String text){return switch(lower(text)){case "lider","líder"->Role.LEADER;case "colider","colíder"->Role.COLEADER;case "oficial"->Role.OFFICER;case "membro"->Role.MEMBER;default->throw error("Cargo inválido: membro, oficial ou colider.");};}
    static String roleName(Role role){return switch(role){case LEADER->"Líder";case COLEADER->"Colíder";case OFFICER->"Oficial";case MEMBER->"Membro";};}
    private static Access parseAccess(String text){return switch(lower(text)){case "portas","mecanismos"->Access.MECHANISMS;case "recipientes"->Access.CONTAINERS;case "construcao","construção"->Access.BUILD;default->throw error("Permissão inválida: portas, recipientes ou construcao.");};}
    private static String accessName(Access a){return switch(a){case BUILD->"construção";case CONTAINERS->"recipientes";case MECHANISMS->"portas";};}
    private static String date(long millis){return millis==0?"—":DATE.format(Instant.ofEpochMilli(millis));}
    private static boolean involves(War w,UUID g){return w.attacker.equals(g)||w.defender.equals(g);}
    private static Chunk chunk(Location l){return new Chunk(l.getWorld().getUID(),l.getBlockX()>>4,l.getBlockZ()>>4);}
    private static <T> List<T> page(List<T> list,int page){long start=(long)(page-1)*10;if(start>=list.size())return List.of();return list.subList((int)start,(int)Math.min(list.size(),start+10));}
    private static String[] concat(String first,String[] rest){String[] result=new String[rest.length+1];result[0]=first;System.arraycopy(rest,0,result,1,rest.length);return result;}
    private static String[] normalize(String command,String[] args){return switch(lower(command)){case "claim"->concat("claim",args);case "unclaim"->concat("unclaim",args);case "claims"->concat("claims",args);default->args;};}

    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] raw) {
        if(!plugin.operational())return List.of();
        String canonical=lower(cmd.getName());
        if(canonical.equals("roubar"))return raw.length==1&&sender.hasPermission("territorio.roubar")?matching(List.of("status"),raw[0]):List.of();
        if(canonical.equals("verchunk"))return List.of();
        String[] a=normalize(canonical,raw); if(a.length==0)return List.of();
        List<String> options=new ArrayList<>();
        if(a.length==1) {
            if(sender.hasPermission("territorio.use"))options.addAll(List.of("ajuda","info","guerras"));
            if(sender.hasPermission("territorio.claim"))options.addAll(List.of("claim","unclaim","claims","confiar","desconfiar"));
            if(sender instanceof Player p&&sender.hasPermission("territorio.grupo")) {
                Group g=d.group(p.getUniqueId());options.addAll(List.of("lista","membros"));
                if(g==null)options.addAll(List.of("criar","aceitar","recusar"));
                else {
                    Role role=g.members.get(p.getUniqueId()).role;options.addAll(List.of("aliados","guerra"));
                    if(!d.inWar(g.id))options.add("sair");
                    if(role!=Role.MEMBER)options.add("convidar");
                    if(d.mayManage(p.getUniqueId()))options.addAll(List.of("expulsar","cargo"));
                    if(d.isLeader(p.getUniqueId())){options.add("paz");if(!d.inWar(g.id))options.addAll(List.of("lider","dissolver"));}
                }
            }
            if(hasAdmin(sender))options.add("admin");
        } else if(a[0].equalsIgnoreCase("admin")) {
            Map<String,String> nodes=Map.of("inspecionar","inspect","nome","rename","tag","rename","lider","leader","removerclaim","unclaim","guerras","wars","saques","raids","reload","reload","bypass","bypass","encerrarguerra","force");
            if(a.length==2)nodes.forEach((command,node)->{if(sender.hasPermission("territorio.admin."+node))options.add(command);});
            else if(a.length==3&&nodes.containsKey(lower(a[1]))&&sender.hasPermission("territorio.admin."+nodes.get(lower(a[1])))&&Set.of("inspecionar","nome","tag","lider","encerrarguerra").contains(lower(a[1])))options.addAll(groupTags());
            else if(a.length==4&&a[1].equalsIgnoreCase("encerrarguerra")&&sender.hasPermission("territorio.admin.force"))options.addAll(groupTags());
            else if(a.length==4&&a[1].equalsIgnoreCase("lider")&&sender.hasPermission("territorio.admin.leader")) {Group g=d.groupByName(a[2]);if(g!=null)g.members.keySet().forEach(id->options.add(name(id)));}
            if(a.length>=4&&sender.hasPermission("territorio.admin.force"))options.add("--forcar");
        } else if(sender instanceof Player p) {
            Group g=d.group(p.getUniqueId());String action=lower(a[0]);
            if(a.length==2)switch(action){
                case "claim"->{if(sender.hasPermission("territorio.claim")&&g!=null)options.add("pessoal");}
                case "info","membros","guerras"->options.addAll(groupTags());
                case "convidar"->{if(g!=null&&sender.hasPermission("territorio.grupo")&&g.members.get(p.getUniqueId()).role!=Role.MEMBER)Bukkit.getOnlinePlayers().stream().filter(q->d.group(q.getUniqueId())==null).forEach(q->options.add(q.getName()));}
                case "confiar","desconfiar"->{if(sender.hasPermission("territorio.claim"))Bukkit.getOnlinePlayers().forEach(q->options.add(q.getName()));}
                case "expulsar","cargo","lider"->{if(g!=null&&sender.hasPermission("territorio.grupo")&&d.mayManage(p.getUniqueId()))g.members.keySet().stream().filter(id->!id.equals(p.getUniqueId())).forEach(id->options.add(name(id)));}
                case "dissolver","desfazer"->{if(d.isLeader(p.getUniqueId())&&!d.inWar(g.id))options.add("confirmar");}
                case "guerra"->{if(g!=null){options.add("status");if(d.isLeader(p.getUniqueId()))options.addAll(List.of("declarar","paz","aceitarpaz","recusarpaz"));}}
                case "paz"->{if(d.isLeader(p.getUniqueId()))options.addAll(List.of("propor","aceitar","recusar"));}
                case "aliados","alianca"->{if(g!=null){options.add("lista");if(d.isLeader(p.getUniqueId()))options.addAll(List.of("propor","aceitar","recusar","remover","permissao"));}}
                default->{}
            };
            if(a.length==3) {
                if(action.equals("cargo")&&d.mayManage(p.getUniqueId())){options.addAll(List.of("membro","oficial"));if(d.isLeader(p.getUniqueId()))options.add("colider");}
                if(action.equals("lider")&&d.isLeader(p.getUniqueId()))options.add("confirmar");
                if(Set.of("guerra","paz","aliados","alianca").contains(action)&&d.isLeader(p.getUniqueId()))options.addAll(groupTags());
            }
            if(a.length==4&&Set.of("aliados","alianca").contains(action)&&a[1].equalsIgnoreCase("permissao")&&d.isLeader(p.getUniqueId()))options.addAll(List.of("portas","recipientes","construcao"));
            if(a.length==5&&Set.of("aliados","alianca").contains(action)&&a[1].equalsIgnoreCase("permissao")&&d.isLeader(p.getUniqueId()))options.addAll(List.of("sim","nao"));
        }
        return matching(options,a[a.length-1]);
    }
    private List<String> groupTags(){return d.s.groups.values().stream().map(g->g.tag).sorted().toList();}
    private static List<String> matching(Collection<String> candidates,String prefix){return candidates.stream().filter(s->lower(s).startsWith(lower(prefix))).distinct().sorted().toList();}
    private static boolean hasAdmin(CommandSender s){return List.of("inspect","rename","leader","unclaim","wars","raids","reload","bypass","force").stream().anyMatch(n->s.hasPermission("territorio.admin."+n));}
    @Override public void close(){stopParticles();pending.values().forEach(BukkitTask::cancel);pending.clear();viewing.clear();announced.clear();if(expansion!=null)expansion.unregister();}
}
