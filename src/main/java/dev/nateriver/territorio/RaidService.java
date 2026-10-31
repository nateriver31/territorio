package dev.nateriver.territorio;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

/** All access grants are main-thread, volatile state; persisted records never grant access. */
public final class RaidService implements Listener {
    private static final int[] MILESTONES = {240, 180, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private final TerritorioPlugin plugin;
    private final Domain domain;
    private final Map<UUID, Session> byInvader = new HashMap<>();
    private final Map<UUID, Session> byVictim = new HashMap<>();
    private final NamespacedKey miningKey;
    private final BukkitTask timer;
    private final BukkitTask miningTimer;
    private boolean stopped;

    /** Time arithmetic isolated from Bukkit so expiration and reconnect races are testable. */
    static final class Session {
        final UUID invader;
        final UUID victim;
        final long started;
        final long deadline;
        final long defenseProtection;
        final long offlineGrace;
        final int blockLimit;
        final Set<Integer> sent = new HashSet<>();
        final Domain.RaidRecord record;
        long offlineUntil;
        int blocks;
        boolean ended;
        boolean armed;

        Session(UUID invader, UUID victim, long now, long duration, long defenseProtection,
                long offlineGrace, int blockLimit, Domain.RaidRecord record) {
            this.invader = invader;
            this.victim = victim;
            this.started = now;
            this.deadline = now + duration;
            this.defenseProtection = defenseProtection;
            this.offlineGrace = offlineGrace;
            this.blockLimit = blockLimit;
            this.record = record;
        }

        boolean alive(long now) {
            return !ended && now < deadline && (offlineUntil == 0 || now < offlineUntil);
        }

        void ownerDisconnected(long now) {
            if (offlineUntil == 0) offlineUntil = Math.min(deadline, now + offlineGrace);
        }

        boolean ownerReconnected(long now) {
            if (!alive(now)) return false;
            offlineUntil = 0;
            return true;
        }

        int remainingSeconds(long now) {
            return (int) Math.max(0, (deadline - now + 999) / 1000);
        }

        boolean canSpend(int amount, long now) {
            return alive(now) && amount > 0 && amount <= blockLimit - blocks;
        }

        List<Integer> notifications(long now) {
            List<Integer> result = new ArrayList<>();
            if (!alive(now)) return result;
            int remaining = remainingSeconds(now);
            int original = (int) ((deadline - started + 999) / 1000);
            for (int seconds : MILESTONES) {
                if (seconds < original && remaining <= seconds && sent.add(seconds)) result.add(seconds);
            }
            return result;
        }
    }

    public RaidService(TerritorioPlugin plugin, Domain domain) {
        this.plugin = plugin;
        this.domain = domain;
        this.miningKey = new NamespacedKey(plugin, "raid_mining");
        // Recover incomplete historical records after a crash; no grant is reconstructed.
        boolean recovered = false;
        for (Domain.RaidRecord record : domain.s.raids) {
            if (record.ended == 0) {
                record.ended = domain.now();
                record.result = "REINICIO";
                recovered = true;
            }
        }
        if (recovered) domain.changed();
        Bukkit.getOnlinePlayers().forEach(this::clearMining);
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
        // Only raiders, not all online players: remove mining penalties as soon as aim changes.
        miningTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Session session : List.copyOf(byInvader.values())) {
                Player player = Bukkit.getPlayer(session.invader);
                if (player != null) refreshMining(player);
            }
        }, 1L, 1L);
    }

    public void start(Player invader) {
        ensureMainThread();
        if (stopped) throw fail("O sistema de saques está sendo encerrado.");
        if(invader.getGameMode()!=org.bukkit.GameMode.SURVIVAL&&invader.getGameMode()!=org.bukkit.GameMode.ADVENTURE)throw fail("O saque exige modo sobrevivência ou aventura.");
        if (!invader.hasPermission("territorio.roubar")) throw fail("Você não pode iniciar saques.");
        expireSessions();
        UUID invaderId = invader.getUniqueId();
        Domain.Claim claim = domain.claim(chunk(invader.getLocation().getBlock()));
        if (claim == null || claim.kind != Domain.Kind.SOLO || domain.group(claim.owner) != null)
            throw fail("Entre em um claim de um jogador solo para iniciar o saque.");
        UUID victimId = claim.owner;
        if (victimId.equals(invaderId)) throw fail("Você não pode saquear seu próprio território.");
        Player victim = Bukkit.getPlayer(victimId);
        if (victim == null || !victim.isOnline()) throw fail("O proprietário precisa estar online.");
        if (byInvader.containsKey(invaderId)) throw fail("Você já tem um saque ativo. Use /roubar status.");
        if (byVictim.containsKey(victimId)) throw fail("Este proprietário já está sendo saqueado.");
        long now = domain.now();
        requireCooldown(domain.s.victimCooldown.get(victimId), now, "Este proprietário está protegido");
        requireCooldown(domain.s.raiderCooldown.get(invaderId), now, "Você está em cooldown de saque");
        domain.s.names.put(invaderId, invader.getName());
        domain.s.names.put(victimId, victim.getName());
        Domain.RaidRecord record = new Domain.RaidRecord();
        record.invader = invaderId;
        record.victim = victimId;
        record.started = now;
        record.result = "EM_ANDAMENTO";
        long duration = secondsSetting("raid.duration-seconds", 300, 1, 3600);
        Session session = new Session(invaderId, victimId, now, duration,
            hoursSetting("raid.defense-cooldown-hours", 24),
            secondsSetting("raid.owner-offline-seconds", 60, 1, 300),
            Math.clamp(plugin.settings().getInt("raid.block-limit", 30), 1, 10000), record);
        domain.s.raids.add(record);
        domain.s.victimCooldown.put(victimId, now + hoursSetting("raid.owner-cooldown-hours", 12));
        domain.s.raiderCooldown.put(invaderId, now + hoursSetting("raid.invader-cooldown-hours", 2));
        // Queue cooldowns and the record before installing ephemeral permissions.
        domain.changed();
        byInvader.put(invaderId, session);
        byVictim.put(victimId, session);
        plugin.audit("SISTEMA", "SAQUE_INICIO", victimId.toString(), "", "invasor=" + invaderId);
        // Reserve participants now, but grant no access until the cooldown reaches durable storage.
        plugin.afterSaved(()->activate(session));
    }

    private void activate(Session session) {
        if(session.ended||!session.alive(domain.now()))return;
        Player invader=Bukkit.getPlayer(session.invader),victim=Bukkit.getPlayer(session.victim);
        if(invader==null||victim==null||!invader.isOnline()||!victim.isOnline()) {finish(session,"DESCONEXAO_ANTES_INICIO",false);return;}
        if(invader.getGameMode()!=org.bukkit.GameMode.SURVIVAL&&invader.getGameMode()!=org.bukkit.GameMode.ADVENTURE) {finish(session,"MODO_DE_JOGO",false);return;}
        session.armed=true;
        UUID victimId=session.victim;long duration=session.deadline-session.started;
        Bukkit.broadcast(Component.text("⚔ O território de " + name(victimId) + " está sendo saqueado!", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("⏳ Tempo de saque: " + durationText((int) (duration / 1000)) + ".", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text("A identidade do invasor é desconhecida.", NamedTextColor.GRAY));
        victim.showTitle(shortTitle("⚠ SEU TERRITÓRIO ESTÁ SENDO SAQUEADO!", NamedTextColor.RED));
        refreshMining(invader);
    }

    public void status(Player player) {
        expireSessions();
        Session attacking = byInvader.get(player.getUniqueId());
        Session defending = byVictim.get(player.getUniqueId());
        if (attacking == null && defending == null) {
            player.sendMessage(Component.text("Você não participa de nenhum saque ativo.", NamedTextColor.GRAY));
            long now = domain.now();
            showCooldown(player, "Próximo saque", domain.s.raiderCooldown.get(player.getUniqueId()), now);
            showCooldown(player, "Sua proteção", domain.s.victimCooldown.get(player.getUniqueId()), now);
            return;
        }
        if (attacking != null) player.sendMessage(Component.text("Seu saque: "
            + attacking.remainingSeconds(domain.now()) + "s restantes; " + attacking.blocks + "/"
            + attacking.blockLimit + " blocos quebrados.", NamedTextColor.YELLOW));
        if (defending != null) player.sendMessage(Component.text("Seu território: "
            + defending.remainingSeconds(domain.now()) + "s restantes; " + defending.blocks + "/"
            + defending.blockLimit + " blocos quebrados. Invasor desconhecido.", NamedTextColor.YELLOW));
    }

    /** True only for the one invader, in this victim's SOLO claims, before every deadline. */
    public boolean canRaid(UUID player, Domain.Claim claim) {
        if (stopped || claim == null || claim.kind != Domain.Kind.SOLO) return false;
        Session session = byInvader.get(player);
        return session != null && session.armed && session.victim.equals(claim.owner) && session.alive(domain.now())
            && domain.group(claim.owner) == null;
    }

    public boolean canOpen(UUID player, Domain.Claim claim) {
        return canRaid(player, claim);
    }

    public boolean isVictim(UUID player) {
        Session session = byVictim.get(player);
        return session != null && session.alive(domain.now());
    }

    public boolean canBreak(UUID player, Block block) {
        Domain.Claim claim = domain.claim(chunk(block));
        if (!canRaid(player, claim) || isStorage(block)) return false;
        Session session = byInvader.get(player);
        return session.canSpend(breakCost(block), domain.now());
    }

    /** Called only for an uncancelled BlockBreakEvent at MONITOR. Never breaks a block itself. */
    public void markBroken(UUID player, Block block) {
        if (!canRaid(player, domain.claim(chunk(block)))) return;
        Session session = byInvader.get(player);
        int cost = breakCost(block);
        if (!session.canSpend(cost, domain.now())) return;
        session.blocks += cost;
        session.record.blocks = session.blocks;
        domain.changed();
        if (session.blocks >= session.blockLimit) {
            tell(session.invader, Component.text("Limite de " + session.blockLimit
                + " blocos atingido. Os recipientes continuam acessíveis até o fim do saque.", NamedTextColor.YELLOW));
            Player online = Bukkit.getPlayer(player);
            if (online != null) clearMining(online);
        }
    }

    /** Containers are never valid loot-by-destruction targets, including non-InventoryHolder storage. */
    public static boolean isStorage(Block block) {
        if (block.getState() instanceof InventoryHolder) return true;
        Material material = block.getType();
        return material == Material.ENDER_CHEST || material == Material.DECORATED_POT
            || material == Material.CHISELED_BOOKSHELF || material == Material.JUKEBOX
            || material == Material.LECTERN;
    }

    static int breakCost(Block block) {
        // Doors and beds remove two blocks in one vanilla BlockBreakEvent.
        if (block.getBlockData() instanceof Bed bed) {
            BlockFace direction = bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace();
            return block.getRelative(direction).getType() == block.getType() ? 2 : 1;
        }
        if (block.getBlockData() instanceof Bisected data
                && !(data instanceof org.bukkit.block.data.type.Stairs)
                && !(data instanceof org.bukkit.block.data.type.TrapDoor)) {
            BlockFace direction = data.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN;
            return block.getRelative(direction).getType() == block.getType() ? 2 : 1;
        }
        return 1;
    }

    public void refreshMining(Player player) {
        Session session = byInvader.get(player.getUniqueId());
        Block target = session != null && session.armed && session.alive(domain.now()) ? player.getTargetBlockExact(6) : null;
        boolean slow = target != null && canBreak(player.getUniqueId(), target);
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(miningKey);
        if (slow && current == null) {
            attribute.addTransientModifier(new AttributeModifier(miningKey, -0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        } else if (!slow && current != null) {
            attribute.removeModifier(current);
        }
    }

    private void clearMining(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) {
            AttributeModifier modifier = attribute.getModifier(miningKey);
            if (modifier != null) attribute.removeModifier(modifier);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(BlockDamageEvent event) {
        refreshMining(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session invading = byInvader.get(id);
        if (invading != null) finish(invading, "DESCONEXAO_INVASOR", false);
        Session defending = byVictim.get(id);
        if (defending != null) defending.ownerDisconnected(domain.now());
        clearMining(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        // Attribute modifiers may survive a process crash in player data; remove only our key.
        clearMining(event.getPlayer());
        Session session = byVictim.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (!session.ownerReconnected(domain.now())) finish(session, "PROPRIETARIO_OFFLINE", false);
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled=true)
    public void mode(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        if(event.getNewGameMode()!=org.bukkit.GameMode.SURVIVAL&&event.getNewGameMode()!=org.bukkit.GameMode.ADVENTURE){Session session=byInvader.get(event.getPlayer().getUniqueId());if(session!=null)finish(session,"MODO_DE_JOGO",false);}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        Session session = byInvader.get(event.getEntity().getUniqueId());
        if (session == null) return;
        if (!session.alive(domain.now())) {
            finish(session, expirationReason(session), false);
            return;
        }
        // Paper's killer attribution covers vanilla indirect/projectile kills; do not guess from last damage.
        Player killer = event.getEntity().getKiller();
        boolean defended = session.armed && killer != null && !killer.getUniqueId().equals(session.invader);
        finish(session, defended ? "DEFESA_BEM_SUCEDIDA" : "MORTE_AMBIENTAL", defended);
    }

    private void tick() {
        if (stopped) return;
        for (Session session : List.copyOf(byInvader.values())) {
            long now = domain.now();
            if (!session.alive(now)) {
                finish(session, expirationReason(session), false);
                continue;
            }
            Player invader = Bukkit.getPlayer(session.invader);
            if (invader == null || !invader.isOnline()) {
                finish(session, "DESCONEXAO_INVASOR", false);
                continue;
            }
            if(invader.getGameMode()!=org.bukkit.GameMode.SURVIVAL&&invader.getGameMode()!=org.bukkit.GameMode.ADVENTURE) {finish(session,"MODO_DE_JOGO",false);continue;}
            Player victim = Bukkit.getPlayer(session.victim);
            if (victim == null || !victim.isOnline()) session.ownerDisconnected(now);
            if (!session.alive(now)) {
                finish(session, expirationReason(session), false);
                continue;
            }
            if(!session.armed)continue;
            for (int seconds : session.notifications(now)) {
                Component message = Component.text("⏳ Saque: " + durationText(seconds) + " restante" + (seconds == 1 || seconds == 60 ? "." : "s."), NamedTextColor.YELLOW);
                tell(session.invader, message);
                tell(session.victim, message);
            }
        }
    }

    private void expireSessions() {
        for (Session session : List.copyOf(byInvader.values())) {
            if (!session.alive(domain.now())) finish(session, expirationReason(session), false);
        }
    }

    private String expirationReason(Session session) {
        return domain.now() >= session.deadline ? "TEMPO_ESGOTADO" : "PROPRIETARIO_OFFLINE";
    }

    private void finish(Session session, String reason, boolean defended) {
        if (session.ended) return;
        session.ended = true;
        byInvader.remove(session.invader, session);
        byVictim.remove(session.victim, session);
        Player invader = Bukkit.getPlayer(session.invader);
        if (invader != null) clearMining(invader);
        session.record.ended = domain.now();
        session.record.blocks = session.blocks;
        session.record.result = reason;
        if (defended) {
            // Both normal and extended protection are anchored to this event's original start.
            domain.s.victimCooldown.merge(session.victim, session.started + session.defenseProtection, Math::max);
        }
        domain.changed();
        plugin.audit("SISTEMA", "SAQUE_FIM", session.victim.toString(), "invasor=" + session.invader,
            "resultado=" + reason + "; blocos=" + session.blocks + "; inicio=" + session.started);
        closeUnauthorizedViews(session);
        if(!session.armed)return;
        if (defended) {
            Bukkit.broadcast(Component.text("✔ DEFESA BEM-SUCEDIDA!", NamedTextColor.GREEN));
            Bukkit.broadcast(Component.text("O território de " + name(session.victim) + " foi defendido!", NamedTextColor.WHITE));
            Bukkit.broadcast(Component.text("O invasor era ", NamedTextColor.WHITE)
                .append(Component.text(name(session.invader), NamedTextColor.YELLOW)).append(Component.text(".", NamedTextColor.WHITE)));
            Bukkit.broadcast(Component.text("Proteção contra novos saques: "
                + durationText((int) (session.defenseProtection / 1000)) + ".", NamedTextColor.GRAY));
            Player victim = Bukkit.getPlayer(session.victim);
            if (victim != null) victim.showTitle(shortTitle("✔ TERRITÓRIO DEFENDIDO!", NamedTextColor.GREEN));
        } else {
            Bukkit.broadcast(Component.text("O saque ao território de " + name(session.victim) + " terminou.", NamedTextColor.GRAY));
        }
    }

    private void closeUnauthorizedViews(Session session) {
        Player player = Bukkit.getPlayer(session.invader);
        if (player == null) return;
        // Closing an open view does not return already looted items or modify inventory contents.
        // Only close storage views associated with this victim's territory.
        var inventory = player.getOpenInventory().getTopInventory();
        if(inventory==null)return;
        var location = inventory.getLocation();
        if (location == null) return;
        Domain.Claim claim = domain.claim(new Domain.Chunk(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (claim != null && claim.kind == Domain.Kind.SOLO && claim.owner.equals(session.victim)
                && !domain.canAccess(session.invader, claim, Domain.Access.CONTAINERS)) player.closeInventory();
    }

    public void stopAll() {
        ensureMainThread();
        if (stopped) return;
        stopped = true;
        timer.cancel();
        miningTimer.cancel();
        for (Session session : List.copyOf(byInvader.values())) finish(session, "REINICIO", false);
        Bukkit.getOnlinePlayers().forEach(this::clearMining);
    }

    private static Domain.Chunk chunk(Block block) {
        return new Domain.Chunk(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private String name(UUID player) {
        return domain.s.names.getOrDefault(player, player.toString());
    }

    private static void tell(UUID id, Component message) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) player.sendMessage(message);
    }

    private static Title shortTitle(String text, NamedTextColor color) {
        return Title.title(Component.text(text, color), Component.empty(),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(100)));
    }

    private static String durationText(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return (seconds / 3600) + " horas";
        if (seconds >= 60 && seconds % 60 == 0) return (seconds / 60) + (seconds == 60 ? " minuto" : " minutos");
        return seconds + (seconds == 1 ? " segundo" : " segundos");
    }

    private static void showCooldown(Player player, String label, Long until, long now) {
        if (until != null && until > now) player.sendMessage(Component.text(label + ": "
            + durationText((int) ((until - now + 999) / 1000)) + ".", NamedTextColor.GRAY));
    }

    private static void requireCooldown(Long until, long now, String message) {
        if (until != null && now < until) throw fail(message + " por mais "
            + durationText((int) ((until - now + 999) / 1000)) + ".");
    }

    private long secondsSetting(String key, int fallback, int minimum, int maximum) {
        return Math.clamp(plugin.settings().getLong(key, fallback), minimum, maximum) * 1000;
    }

    private long hoursSetting(String key, int fallback) {
        return Math.clamp(plugin.settings().getLong(key, fallback), 1, 24 * 365) * 3_600_000;
    }

    private static IllegalArgumentException fail(String message) {
        return new IllegalArgumentException(message);
    }

    private static void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Raid mutation outside server thread");
    }
}
