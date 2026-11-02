package dev.nateriver.territorio;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** All Bukkit access is confined to the server thread. Combat policy only forbids same-group damage. */
public final class ProtectionListener implements Listener {
    private final TerritorioPlugin plugin;
    private final Domain domain;
    private final RaidService raids;
    private final Map<UUID, Long> deniedAt = new HashMap<>();
    private final Map<PotionSplashEvent, Map<LivingEntity,Double>> potionBuffs = new IdentityHashMap<>();
    private final Map<AreaEffectCloudApplyEvent, List<LivingEntity>> cloudBuffs = new IdentityHashMap<>();
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    public ProtectionListener(TerritorioPlugin plugin, Domain domain, RaidService raids) {
        this.plugin = plugin; this.domain = domain; this.raids = raids;
    }
    private Domain.Chunk key(Block b) { return new Domain.Chunk(b.getWorld().getUID(), b.getX() >> 4, b.getZ() >> 4); }
    private Domain.Claim claim(Block b) { return domain.claim(key(b)); }
    private Domain.Claim claim(Location l) { return claim(l.getBlock()); }
    private boolean same(Domain.Claim a, Domain.Claim b) {
        return a == null ? b == null : b != null && a.kind == b.kind && a.owner.equals(b.owner);
    }
    private boolean access(Player p, Domain.Claim c, Domain.Access action) {
        if (c == null || plugin.bypassing(p.getUniqueId())) return true;
        // An active invader cannot turn a previous trust grant into unrestricted looting.
        if (raids.canRaid(p.getUniqueId(), c))
            return action == Domain.Access.CONTAINERS && raids.canOpen(p.getUniqueId(), c);
        return domain.canAccess(p.getUniqueId(), c, action);
    }
    private boolean access(Player p, Block b, Domain.Access action) { return access(p, claim(b), action); }
    private void denied(Player p) {
        long now = System.currentTimeMillis();
        if (now - deniedAt.getOrDefault(p.getUniqueId(), 0L) >= 1500) {
            deniedAt.put(p.getUniqueId(), now);
            p.sendMessage("§cEste território está protegido.");
        }
    }
    private boolean storage(Block b) {
        return b.getState() instanceof InventoryHolder || switch (b.getType().name()) {
            case "ENDER_CHEST", "DECORATED_POT", "CHISELED_BOOKSHELF", "JUKEBOX", "LECTERN" -> true;
            default -> false;
        };
    }
    private boolean mechanism(Block b) {
        String n = b.getType().name();
        return b.getBlockData() instanceof Openable || b.getBlockData() instanceof org.bukkit.block.data.type.Switch
                || n.endsWith("PRESSURE_PLATE") || n.equals("TRIPWIRE") || n.equals("TRIPWIRE_HOOK")
                || Set.of("BELL", "NOTE_BLOCK", "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR").contains(n);
    }
    private boolean raidTrigger(Block b) {
        return b.getBlockData() instanceof org.bukkit.block.data.type.Switch
                || b.getType().name().endsWith("PRESSURE_PLATE");
    }
    private boolean triggerAccess(Player p, Block b) {
        return access(p,b,Domain.Access.MECHANISMS)
                || (raidTrigger(b) && raids.canRaid(p.getUniqueId(),claim(b)));
    }
    private boolean raided(Block b) {
        Domain.Claim c = claim(b);
        return c != null && c.kind == Domain.Kind.SOLO && raids.isVictim(c.owner);
    }
    private boolean raidBreak(Player p, Block b) {
        if (storage(b) || !raids.canBreak(p.getUniqueId(), b)) return false;
        // Breaking a support must not drop a container, gravity stack, or attached blocks for free.
        for (BlockFace face : FACES) {
            Block other = b.getRelative(face);
            if (other.isEmpty() || !same(claim(b), claim(other))) continue;
            String n = other.getType().name();
            var data = other.getBlockData();
            boolean attached = data instanceof Attachable || data instanceof FaceAttachable
                    || data instanceof Rail || data instanceof org.bukkit.block.data.Ageable || n.endsWith("_SIGN")
                    || n.endsWith("_TORCH") || n.equals("TORCH") || n.equals("REDSTONE_WIRE")
                    || n.equals("SCAFFOLDING") || n.equals("POINTED_DRIPSTONE")
                    || n.equals("VINE") || n.endsWith("_CARPET") || n.endsWith("_FLOWER");
            boolean above = face == BlockFace.UP;
            boolean companion = b.getBlockData() instanceof Bisected own && data instanceof Bisected otherHalf
                    && b.getType() == other.getType() && own.getHalf() != otherHalf.getHalf()
                    && (face == BlockFace.UP || face == BlockFace.DOWN);
            // Bisected door/crop targets themselves are counted by RaidService; their support is protected.
            if (storage(other) || other.getType().hasGravity() || attached
                    || (above && data instanceof Bisected && !(data instanceof org.bukkit.block.data.type.Stairs)
                    && !(data instanceof org.bukkit.block.data.type.TrapDoor) && !companion)) return false;
        }
        return true;
    }

    private Block partner(Block b) {
        if(b.getBlockData() instanceof org.bukkit.block.data.type.Bed bed) {
            Block other=b.getRelative(bed.getPart()==org.bukkit.block.data.type.Bed.Part.FOOT?bed.getFacing():bed.getFacing().getOppositeFace());
            return other.getType()==b.getType()?other:null;
        }
        if(b.getBlockData() instanceof Bisected bisected && !(bisected instanceof org.bukkit.block.data.type.Stairs) && !(bisected instanceof org.bukkit.block.data.type.TrapDoor)) {
            Block other=b.getRelative(bisected.getHalf()==Bisected.Half.BOTTOM?BlockFace.UP:BlockFace.DOWN);return other.getType()==b.getType()?other:null;
        }
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void takeBook(PlayerTakeLecternBookEvent e) {if(!access(e.getPlayer(),e.getLectern().getBlock(),Domain.Access.CONTAINERS))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fish(PlayerFishEvent e) {
        if(e.getCaught()!=null&&!(e.getCaught() instanceof Player)&&!access(e.getPlayer(),claim(e.getCaught().getLocation()),Domain.Access.BUILD))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e) {
        Player p=e.getPlayer(); Domain.Claim c=claim(e.getBlock());
        if (c == null || plugin.bypassing(p.getUniqueId())) return;
        boolean allowed = raids.canRaid(p.getUniqueId(), c) ? raidBreak(p,e.getBlock()) : access(p,c,Domain.Access.BUILD);
        Block partner=partner(e.getBlock());
        if(partner!=null&&!same(c,claim(partner)))allowed &= access(p,partner,Domain.Access.BUILD);
        if (!allowed) {e.setCancelled(true);denied(p);}
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void countBreak(BlockBreakEvent e) {
        if (!plugin.bypassing(e.getPlayer().getUniqueId())) raids.markBroken(e.getPlayer().getUniqueId(),e.getBlock());
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damageBlock(BlockDamageEvent e) {
        raids.refreshMining(e.getPlayer());
        Domain.Claim c=claim(e.getBlock());
        if (c != null && !plugin.bypassing(e.getPlayer().getUniqueId())) {
            boolean ok=raids.canRaid(e.getPlayer().getUniqueId(),c) ? raidBreak(e.getPlayer(),e.getBlock())
                    : access(e.getPlayer(),c,Domain.Access.BUILD);
            if (!ok) e.setCancelled(true);
            else if (raids.canRaid(e.getPlayer().getUniqueId(),c)) e.setInstaBreak(false);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        if (!access(e.getPlayer(),e.getBlockPlaced(),Domain.Access.BUILD)) {e.setCancelled(true);denied(e.getPlayer());}
        if (e instanceof BlockMultiPlaceEvent multi) for (BlockState state:multi.getReplacedBlockStates())
            if (!access(e.getPlayer(),state.getBlock(),Domain.Access.BUILD)) {e.setCancelled(true);break;}
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Block b=e.getClickedBlock(); if (b==null) return;
        // Left-click damage is independently checked in BlockDamage/Break, including the raid budget.
        if (e.getAction()==Action.LEFT_CLICK_BLOCK) {
            if (b.getType()==Material.DRAGON_EGG && !access(e.getPlayer(),b,Domain.Access.BUILD)) {
                e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }
        Domain.Access a=storage(b) ? Domain.Access.CONTAINERS : mechanism(b) ? Domain.Access.MECHANISMS : Domain.Access.BUILD;
        boolean allowed = a == Domain.Access.MECHANISMS ? triggerAccess(e.getPlayer(),b) : access(e.getPlayer(),b,a);
        if (!allowed) {
            e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);denied(e.getPlayer());
        }
        // Do not let a permitted container/door interaction grant bucket, bone meal, ignition, or item placement rights.
        if (e.getItem()!=null && !access(e.getPlayer(),b,Domain.Access.BUILD)) {
            Material item=e.getItem().getType(); String name=item.name();
            if (name.endsWith("BUCKET") || item==Material.FLINT_AND_STEEL || item==Material.FIRE_CHARGE
                    || item==Material.BONE_MEAL || item==Material.ARMOR_STAND || name.endsWith("SPAWN_EGG")
                    || item==Material.END_CRYSTAL || name.endsWith("MINECART") || name.endsWith("BOAT")
                    || name.endsWith("RAFT")) e.setUseItemInHand(Event.Result.DENY);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void bucket(PlayerBucketEmptyEvent e) {
        if (!access(e.getPlayer(),e.getBlock(),Domain.Access.BUILD)
                || !access(e.getPlayer(),e.getBlockClicked(),Domain.Access.BUILD)) {e.setCancelled(true);denied(e.getPlayer());}
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e) {
        if (!access(e.getPlayer(),e.getBlock(),Domain.Access.BUILD)) {e.setCancelled(true);denied(e.getPlayer());}
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void harvest(PlayerHarvestBlockEvent e) {
        if (!access(e.getPlayer(),e.getHarvestedBlock(),Domain.Access.BUILD)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void sign(SignChangeEvent e) {
        if (!access(e.getPlayer(),e.getBlock(),Domain.Access.BUILD)) e.setCancelled(true);
    }

    private List<Block> inventoryBlocks(Inventory inventory) {
        List<Block> blocks=new ArrayList<>(); InventoryHolder h=inventory.getHolder();
        if (h instanceof DoubleChest chest) {
            addHolder(blocks,chest.getLeftSide()); addHolder(blocks,chest.getRightSide());
        } else addHolder(blocks,h);
        return blocks;
    }
    private void addHolder(List<Block> blocks, InventoryHolder holder) {
        if (holder instanceof BlockState state) blocks.add(state.getBlock());
        else if (holder instanceof Entity entity && !(entity instanceof Player)) blocks.add(entity.getLocation().getBlock());
    }
    private boolean inventoryAccess(Player p,Inventory inventory) {
        for (Block b:inventoryBlocks(inventory)) if (!access(p,b,Domain.Access.CONTAINERS)) return false;
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void open(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && !inventoryAccess(p,e.getInventory())) {e.setCancelled(true);denied(p);}
    }
    private void closeLater(Player p) { plugin.getServer().getScheduler().runTask(plugin,(Runnable)p::closeInventory); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && !inventoryAccess(p,e.getView().getTopInventory())) {
            e.setCancelled(true);closeLater(p);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && !inventoryAccess(p,e.getView().getTopInventory())) {
            e.setCancelled(true);closeLater(p);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void transfer(InventoryMoveItemEvent e) {
        List<Block> source=inventoryBlocks(e.getSource()), target=inventoryBlocks(e.getDestination());
        if (source.isEmpty() && target.isEmpty()) return;
        // Entity inventories can travel in from the wilderness; do not authorize them merely by location.
        boolean entity = e.getSource().getHolder() instanceof Entity || e.getDestination().getHolder() instanceof Entity;
        List<Block> all=new ArrayList<>(source);all.addAll(target);
        Domain.Claim first=all.isEmpty()?null:claim(all.getFirst());
        for (Block b:all) if (raided(b) || !same(first,claim(b)) || (entity && claim(b)!=null)) {e.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void pickup(InventoryPickupItemEvent e) {
        Domain.Claim item=claim(e.getItem().getLocation());
        for(Block b:inventoryBlocks(e.getInventory()))
            if (raided(b) || !same(item,claim(b)) || (e.getInventory().getHolder() instanceof Entity && item!=null)) {
                e.setCancelled(true);return;
            }
    }
    private Player explosionActor(Entity source) {
        if (source instanceof Creeper creeper) {
            // Explicit ignition takes precedence over a mob's unrelated current target.
            if (creeper.getIgniter()!=null) return actor(creeper.getIgniter());
            if (!creeper.isIgnited() && creeper.getTarget() instanceof Player player) return player;
            return null;
        }
        return actor(source);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void explode(EntityExplodeEvent e) {
        Player player=explosionActor(e.getEntity());
        // Approved cannon exception: TNT without a traceable player, including a lost source.
        boolean automatic=e.getEntity() instanceof TNTPrimed && player==null;
        e.blockList().removeIf(b->raided(b) || (claim(b)!=null && !automatic
                && (player==null || !access(player,b,Domain.Access.BUILD))));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void explodeBlock(BlockExplodeEvent e) {e.blockList().removeIf(b->claim(b)!=null);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void burn(BlockBurnEvent e) {if(raided(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e) {
        Player player=e.getPlayer()!=null?e.getPlayer():actor(e.getIgnitingEntity());
        boolean crossesDispenserBoundary=e.getIgnitingEntity() instanceof Projectile projectile
                && projectile.getShooter() instanceof BlockProjectileSource source
                && !same(claim(source.getBlock()),claim(e.getBlock()));
        if (raided(e.getBlock()) || (player!=null && !access(player,e.getBlock(),Domain.Access.BUILD))
                || (claim(e.getBlock())!=null && crossesDispenserBoundary)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void prime(TNTPrimeEvent e) {
        Player player=explosionActor(e.getPrimingEntity());
        // Priming removes the TNT block before an explosion/BlockBreakEvent can count it.
        if (raided(e.getBlock()) || (player!=null && !access(player,e.getBlock(),Domain.Access.BUILD))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void flow(BlockFromToEvent e) {
        if (claim(e.getToBlock())!=null && (!same(claim(e.getBlock()),claim(e.getToBlock())) || raided(e.getToBlock()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void spread(BlockSpreadEvent e) {
        if(claim(e.getBlock())!=null && (!same(claim(e.getSource()),claim(e.getBlock())) || raided(e.getBlock()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void sponge(SpongeAbsorbEvent e) {
        e.getBlocks().removeIf(s -> claim(s.getBlock())!=null && (!same(claim(e.getBlock()),claim(s.getBlock())) || raided(s.getBlock())));
    }
    private boolean pistonUnsafe(Block piston, Collection<Block> moved, BlockFace direction) {
        Domain.Claim origin=claim(piston);
        if (raided(piston)) return true;
        for(Block b:moved) {
            Block dest=b.getRelative(direction);
            if (raided(b) || raided(dest) || !same(origin,claim(b)) || !same(origin,claim(dest))) return true;
        }
        return !same(origin,claim(piston.getRelative(direction)));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e) {if(pistonUnsafe(e.getBlock(),e.getBlocks(),e.getDirection()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent e) {if(pistonUnsafe(e.getBlock(),e.getBlocks(),e.getDirection().getOppositeFace()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e) {
        Block source=e.getBlock();
        BlockFace face=source.getBlockData() instanceof Directional d?d.getFacing():BlockFace.SELF;
        Block target=source.getRelative(face);
        boolean underRaid=raided(source) || raided(target);
        boolean safeProjectile=source.getType()==Material.DISPENSER && switch(e.getItem().getType()) {
            case ARROW, SPECTRAL_ARROW, SNOWBALL, EGG -> true;
            default -> false;
        };
        // Keep item ejection and destructive payloads blocked during a raid; defensive arrows still work.
        if (underRaid && !safeProjectile) {e.setCancelled(true);return;}
        // Only automatic TNT gets the approved ownership-boundary exception, outside a raid.
        boolean automaticTnt=source.getType()==Material.DISPENSER && e.getItem().getType()==Material.TNT;
        if (!same(claim(source),claim(target)) && (underRaid || !automaticTnt)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void craft(CrafterCraftEvent e) {if(raided(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent e) {
        if(e.getPlayer()!=null) {
            for(BlockState s:e.getBlocks()) if(!access(e.getPlayer(),s.getBlock(),Domain.Access.BUILD)) {e.setCancelled(true);return;}
        } else e.getBlocks().removeIf(s->claim(s.getBlock())!=null && (!same(claim(e.getBlock()),claim(s.getBlock())) || raided(s.getBlock())));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void tree(StructureGrowEvent e) {
        Domain.Claim origin=claim(e.getLocation());
        if(e.getPlayer()!=null) {
            for(BlockState s:e.getBlocks()) if(!access(e.getPlayer(),s.getBlock(),Domain.Access.BUILD)) {e.setCancelled(true);return;}
        } else e.getBlocks().removeIf(s->claim(s.getBlock())!=null && (!same(origin,claim(s.getBlock())) || raided(s.getBlock())));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void decay(LeavesDecayEvent e) {if(raided(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fade(BlockFadeEvent e) {if(raided(e.getBlock()))e.setCancelled(true);}
    private boolean supportedRaidCircuit(Block block) {
        boolean circuit=raidTrigger(block) || block.getBlockData() instanceof Openable || switch(block.getType()) {
            case REDSTONE_WIRE, REDSTONE_TORCH, REDSTONE_WALL_TORCH, REPEATER, COMPARATOR,
                TRIPWIRE, TRIPWIRE_HOOK, REDSTONE_LAMP, OBSERVER, NOTE_BLOCK, TARGET,
                DAYLIGHT_DETECTOR, DISPENSER, DROPPER, PISTON, STICKY_PISTON -> true;
            default -> false;
        };
        return circuit && block.getBlockData().isSupported(block);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void physicalRaid(BlockPhysicsEvent e) {
        // Also prevent an outside support update from detaching a protected sign/container/etc.
        if (claim(e.getBlock())!=null && !same(claim(e.getBlock()),claim(e.getSourceBlock()))) e.setCancelled(true);
        else if (raided(e.getBlock()) && !supportedRaidCircuit(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void entityPlace(EntityPlaceEvent e) {
        Domain.Claim c=claim(e.getEntity().getLocation());
        if(c!=null && (e.getPlayer()==null || !access(e.getPlayer(),c,Domain.Access.BUILD)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent e) {
        if(!access(e.getPlayer(),claim(e.getRightClicked().getLocation()),Domain.Access.BUILD))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void entityBlock(EntityChangeBlockEvent e) {
        if (claim(e.getBlock())!=null) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void entityPhysical(EntityInteractEvent e) {
        Player player=e.getEntity() instanceof Player p?p
                :e.getEntity() instanceof Projectile projectile && projectile.getShooter() instanceof Player p?p:null;
        if(claim(e.getBlock())!=null && (player==null || !raidTrigger(e.getBlock())
                || !triggerAccess(player,e.getBlock()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void form(BlockFormEvent e) {
        if(e instanceof EntityBlockFormEvent form && claim(e.getBlock())!=null) {
            if(!(form.getEntity() instanceof Player p) || !access(p,e.getBlock(),Domain.Access.BUILD)) e.setCancelled(true);
        }
    }
    private Player actor(Entity entity) {
        if(entity instanceof Player p)return p;
        if(entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p)return p;
        if(entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p)return p;
        if(entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p)return p;
        if(entity instanceof Tameable tameable && tameable.getOwner() instanceof Player p)return p;
        if(entity instanceof LightningStrike lightning)return lightning.getCausingPlayer();
        return null;
    }
    private boolean sameGroup(Player source, Player target) {
        if(source==null || source.getUniqueId().equals(target.getUniqueId()))return false;
        Domain.Group group=domain.group(source.getUniqueId()), other=domain.group(target.getUniqueId());
        return group!=null && other!=null && group.id.equals(other.id);
    }
    private boolean friendlyFire(Entity source, Entity target) {
        return target instanceof Player player && sameGroup(actor(source),player);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void groupDamage(EntityDamageByEntityEvent e) {
        if(friendlyFire(e.getDamager(),e.getEntity()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void groupCombust(EntityCombustByEntityEvent e) {
        if(friendlyFire(e.getCombuster(),e.getEntity()))e.setCancelled(true);
    }
    @SuppressWarnings("deprecation")
    private boolean harmful(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(effect->effect.getType().getEffectCategory()==PotionEffectType.Category.HARMFUL);
    }
    @SuppressWarnings("deprecation")
    private boolean hasBuff(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(effect->effect.getType().getEffectCategory()!=PotionEffectType.Category.HARMFUL);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void groupSplash(PotionSplashEvent e) {
        if(!plugin.getServer().getPluginManager().isPluginEnabled("PvPManager"))return;
        Player source=actor(e.getPotion());
        Collection<PotionEffect> effects=e.getPotion().getEffects();
        if(source==null || effects.isEmpty())return;
        Map<LivingEntity,Double> buffs=new LinkedHashMap<>();
        boolean preserveBuffs=hasBuff(effects);
        // Zero intensity removes the target from Paper's affected map, before PvPManager's monitors.
        for(LivingEntity target:List.copyOf(e.getAffectedEntities()))
            if(target instanceof Player player && sameGroup(source,player)) {
                if(preserveBuffs)buffs.put(target,e.getIntensity(target));
                e.setIntensity(target,0);
            }
        if(!buffs.isEmpty())potionBuffs.put(e,buffs);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void groupCloud(AreaEffectCloudApplyEvent e) {
        if(!plugin.getServer().getPluginManager().isPluginEnabled("PvPManager"))return;
        AreaEffectCloud cloud=e.getEntity();
        Player source=actor(cloud);
        if(source==null)return;
        List<PotionEffect> effects=new ArrayList<>(cloud.getCustomEffects());
        if(cloud.getBasePotionType()!=null)effects.addAll(cloud.getBasePotionType().getPotionEffects());
        if(effects.isEmpty())return;
        List<LivingEntity> buffs=new ArrayList<>();
        boolean preserveBuffs=hasBuff(effects);
        e.getAffectedEntities().removeIf(target->{
            if(!(target instanceof Player player) || !sameGroup(source,player))return false;
            if(preserveBuffs)buffs.add(target);
            return true;
        });
        if(!buffs.isEmpty())cloudBuffs.put(e,buffs);
    }
    // Deliberate compatibility exception: after PvPManager 4.0.22's MONITOR, restore buff
    // recipients for vanilla application. Never synthesize effects, durations or combat timers.
    // Individual harmful effects of mixed potions are blocked by groupEffect below.
    @EventHandler(priority=EventPriority.MONITOR)
    public void restorePotionBuffs(PotionSplashEvent e) {
        Map<LivingEntity,Double> buffs=potionBuffs.remove(e);
        if(buffs!=null && !e.isCancelled())buffs.forEach((target,intensity)->{
            if(!e.getAffectedEntities().contains(target))e.setIntensity(target,intensity);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void restoreCloudBuffs(AreaEffectCloudApplyEvent e) {
        List<LivingEntity> buffs=cloudBuffs.remove(e);
        if(buffs!=null && !e.isCancelled())for(LivingEntity target:buffs)
            if(!e.getAffectedEntities().contains(target))e.getAffectedEntities().add(target);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void groupEffect(EntityPotionEffectEvent e) {
        if(e.getNewEffect()!=null && harmful(List.of(e.getNewEffect())) && friendlyFire(e.getSource(),e.getEntity()))e.setCancelled(true);
    }
    private boolean canHarmEntity(Entity target,Entity source) {
        if(target instanceof Player) return true; // Other players remain subject to PvPManager's policy.
        Domain.Claim c=claim(target.getLocation()); if(c==null)return true;
        Player p=actor(source);return p!=null && access(p,c,Domain.Access.BUILD);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void entityDamage(EntityDamageByEntityEvent e) {
        // Reassert after PvPManager 4.0.22's HIGHEST override, before its MONITOR combat tagging.
        if(friendlyFire(e.getDamager(),e.getEntity()) || !canHarmEntity(e.getEntity(),e.getDamager()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void hanging(HangingBreakEvent e) {
        if (claim(e.getEntity().getLocation())==null)return;
        if (!(e instanceof HangingBreakByEntityEvent by) || !canHarmEntity(e.getEntity(),by.getRemover()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void hangingPlace(HangingPlaceEvent e) {
        if(claim(e.getEntity().getLocation())!=null && (e.getPlayer()==null || !access(e.getPlayer(),claim(e.getEntity().getLocation()),Domain.Access.BUILD)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void useEntity(PlayerInteractEntityEvent e) {
        Entity entity=e.getRightClicked();if(entity instanceof Player)return;
        Domain.Access type=entity instanceof InventoryHolder?Domain.Access.CONTAINERS
                : entity instanceof Vehicle?Domain.Access.MECHANISMS:Domain.Access.BUILD;
        if(!access(e.getPlayer(),claim(entity.getLocation()),type)){e.setCancelled(true);denied(e.getPlayer());}
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void useEntityAt(PlayerInteractAtEntityEvent e) {useEntity(e);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void shear(PlayerShearEntityEvent e) {if(!access(e.getPlayer(),claim(e.getEntity().getLocation()),Domain.Access.BUILD))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void leash(PlayerLeashEntityEvent e) {if(!access(e.getPlayer(),claim(e.getEntity().getLocation()),Domain.Access.BUILD))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void unleash(PlayerUnleashEntityEvent e) {if(!access(e.getPlayer(),claim(e.getEntity().getLocation()),Domain.Access.BUILD))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void vehicleDamage(VehicleDamageEvent e) {if(!canHarmEntity(e.getVehicle(),e.getAttacker()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void vehicleDestroy(VehicleDestroyEvent e) {if(!canHarmEntity(e.getVehicle(),e.getAttacker()))e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){deniedAt.remove(e.getPlayer().getUniqueId());}
}
