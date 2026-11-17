package dev.nateriver.territorio;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** New rewards only: never touches combat policy, inventories, loot enchantments or raid rules. */
final class ProgressListener implements Listener {
    private final TerritorioPlugin plugin;
    private final Domain domain;
    private final Set<UUID> handledLives=new HashSet<>();
    private final Set<PlayerDeathEvent> handledEvents=Collections.newSetFromMap(new WeakHashMap<>());
    private final DoubleSupplier roll;
    private final IntUnaryOperator choose;

    ProgressListener(TerritorioPlugin plugin,Domain domain) {this(plugin,domain,Math::random,bound->java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));}
    ProgressListener(TerritorioPlugin plugin,Domain domain,DoubleSupplier roll,IntUnaryOperator choose) {
        this.plugin=plugin;this.domain=domain;this.roll=roll;this.choose=choose;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void death(PlayerDeathEvent event) {
        if(!plugin.operational()||!handledEvents.add(event)||!handledLives.add(event.getEntity().getUniqueId()))return;
        Player victim=event.getEntity(),killer=killer(event);
        if(killer==null||killer.getUniqueId().equals(victim.getUniqueId())||killer.hasMetadata("NPC")||victim.hasMetadata("NPC"))return;
        Domain.Group defeated=domain.group(victim.getUniqueId()),winning=domain.group(killer.getUniqueId());
        if(defeated==null||(winning!=null&&winning.id.equals(defeated.id)))return;
        // Reconcile loss first: never reveal a bonus chunk that this death just unprotected.
        domain.pvpKill(killer.getUniqueId(),victim.getUniqueId());
        UpdateOptions options=plugin.options();
        if(!options.intel())return;
        List<Domain.Chunk> candidates=eligible(defeated,options);
        if(candidates.isEmpty()||roll.getAsDouble()>=options.chance())return;
        Domain.Chunk selected=candidates.get(choose.applyAsInt(candidates.size()));
        event.getDrops().add(book(defeated,victim.getName(),selected));
        plugin.activity("INTEL",killer.getName()+"/"+killer.getUniqueId()+" obteve livro de ["+defeated.tag+"] "+defeated.name
            +"/"+defeated.id+" vítima="+victim.getName()+"/"+victim.getUniqueId()+" | "+plugin.chunkDescription(selected));
    }
    private Player killer(PlayerDeathEvent event) {
        var source=event.getDamageSource();
        if(source.getDamageType().equals(DamageType.GENERIC_KILL))return null;
        if(source.getCausingEntity() instanceof Player p)return p;
        Entity direct=source.getDirectEntity();
        if(direct instanceof Player p)return p;
        if(direct instanceof Projectile projectile&&projectile.getShooter() instanceof Player p)return p;
        if(direct instanceof TNTPrimed tnt&&tnt.getSource() instanceof Player p)return p;
        return null; // No old combat credit, nearest-player guess, NPC or disconnect simulation.
    }
    List<Domain.Chunk> eligible(Domain.Group group,UpdateOptions options) {
        return domain.s.claims.entrySet().stream().filter(entry->{
            Domain.Claim claim=entry.getValue();
            return (claim.owner.equals(group.id)&&((claim.kind==Domain.Kind.GROUP&&options.normalIntel())
                ||(claim.kind==Domain.Kind.BONUS&&options.bonusIntel())))
                ||(claim.kind==Domain.Kind.PERSONAL&&options.personalIntel()&&group.members.containsKey(claim.owner));
        }).map(Map.Entry::getKey).sorted(Comparator.comparing((Domain.Chunk c)->c.world().toString()).thenComparingInt(Domain.Chunk::x).thenComparingInt(Domain.Chunk::z)).toList();
    }
    private ItemStack book(Domain.Group group,String victim,Domain.Chunk chunk) {
        ItemStack item=new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta=(BookMeta)item.getItemMeta();
        meta.setTitle("Informações de ["+group.tag+"]");meta.setAuthor("Território");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        World world=Bukkit.getWorld(chunk.world());
        String dimension=world==null?"Mundo não carregado\nUUID: "+chunk.world():world.getName()+"\n"+world.getKey();
        meta.setPages("Informações do grupo\n\n["+group.tag+"] "+group.name+"\n\nVítima: "+victim,
            "Localização revelada\n\n"+dimension+"\n\nCentro do chunk:\nX: "+((long)chunk.x()*16+8)+"\nZ: "+((long)chunk.z()*16+8));
        item.setItemMeta(meta);return item;
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {handledLives.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {if(!event.getPlayer().isDead())handledLives.remove(event.getPlayer().getUniqueId());}
}
