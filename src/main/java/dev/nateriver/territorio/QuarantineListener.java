package dev.nateriver.territorio;

import org.bukkit.Bukkit;
import org.bukkit.event.*;

/** Fail-closed maintenance guard. Never loads, repairs or deletes map data. */
final class QuarantineListener implements Listener {
    void register(TerritorioPlugin plugin) {
        String[] names={
            "block.BlockPhysicsEvent","block.BlockFadeEvent","block.SpongeAbsorbEvent","block.BlockBreakEvent","block.BlockPlaceEvent","block.BlockDamageEvent","block.BlockBurnEvent","block.BlockIgniteEvent","block.BlockExplodeEvent","block.BlockFromToEvent","block.BlockPistonExtendEvent","block.BlockPistonRetractEvent","block.BlockDispenseEvent","block.BlockGrowEvent","block.BlockSpreadEvent","block.BlockFormEvent","block.EntityBlockFormEvent","block.BlockFertilizeEvent","block.LeavesDecayEvent","block.MoistureChangeEvent",
            "entity.EntityExplodeEvent","entity.EntityChangeBlockEvent","entity.EntityInteractEvent","entity.EntityPlaceEvent",
            "player.PlayerTakeLecternBookEvent","player.PlayerInteractEvent","player.PlayerInteractEntityEvent","player.PlayerInteractAtEntityEvent","player.PlayerBucketEmptyEvent","player.PlayerBucketFillEvent","player.PlayerArmorStandManipulateEvent","player.PlayerHarvestBlockEvent","player.PlayerShearEntityEvent",
            "inventory.InventoryOpenEvent","inventory.InventoryClickEvent","inventory.InventoryDragEvent","inventory.InventoryMoveItemEvent","inventory.InventoryPickupItemEvent",
            "hanging.HangingBreakEvent","hanging.HangingPlaceEvent","world.StructureGrowEvent"};
        for(String name:names)try {
            Class<? extends Event> type=Class.forName("org.bukkit.event."+name).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(type,this,EventPriority.HIGHEST,(listener,event)->{if(!plugin.operational()&&event instanceof Cancellable c)c.setCancelled(true);},plugin,false);
        }catch(ClassNotFoundException e){plugin.getLogger().warning("Guard indisponível: "+name);}
    }
}
