package dev.nateriver.territorio;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;

/** API-interface fakes exercise rules without a server, no world or inventory mutation. */
class RaidBlockRulesTest {
    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, Function<String, Object> call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> call.apply(method.getName()));
    }

    private Block block(Material material, BlockData data, Material relative) {
        Block partner = fake(Block.class, method -> method.equals("getType") ? relative : null);
        return fake(Block.class, method -> switch (method) {
            case "getType" -> material;
            case "getBlockData" -> data;
            case "getRelative" -> partner;
            default -> null;
        });
    }

    @Test void doorRemovalSpendsBothHalves() {
        Door door = fake(Door.class, method -> method.equals("getHalf") ? Bisected.Half.BOTTOM : null);
        assertEquals(2, RaidService.breakCost(block(Material.OAK_DOOR, door, Material.OAK_DOOR)));
        assertEquals(1, RaidService.breakCost(block(Material.OAK_DOOR, door, Material.AIR)));
    }

    @Test void bedRemovalSpendsBothParts() {
        Bed bed = fake(Bed.class, method -> switch (method) {
            case "getPart" -> Bed.Part.FOOT;
            case "getFacing" -> BlockFace.NORTH;
            default -> null;
        });
        assertEquals(2, RaidService.breakCost(block(Material.RED_BED, bed, Material.RED_BED)));
    }

    @Test void stackedStairsAndTrapdoorsAreNotCountedAsTwoBlockStructures() {
        Stairs stairs = fake(Stairs.class, method -> method.equals("getHalf") ? Bisected.Half.BOTTOM : null);
        TrapDoor trapdoor = fake(TrapDoor.class, method -> method.equals("getHalf") ? Bisected.Half.BOTTOM : null);
        assertEquals(1, RaidService.breakCost(block(Material.OAK_STAIRS, stairs, Material.OAK_STAIRS)));
        assertEquals(1, RaidService.breakCost(block(Material.OAK_TRAPDOOR, trapdoor, Material.OAK_TRAPDOOR)));
    }

    @Test void nonInventoryStorageCannotBeDestroyedToExtractContents() {
        for (Material material : new Material[]{Material.DECORATED_POT, Material.CHISELED_BOOKSHELF,
                Material.JUKEBOX, Material.LECTERN, Material.ENDER_CHEST}) {
            assertTrue(RaidService.isStorage(block(material, null, Material.AIR)), material.name());
        }
        assertFalse(RaidService.isStorage(block(Material.STONE, null, Material.AIR)));
    }

    @Test void anyInventoryHolderIsProtectedIncludingContainersAddedByServer() {
        BlockState state = (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(),
            new Class<?>[]{BlockState.class, InventoryHolder.class}, (proxy, method, args) -> null);
        Block container = fake(Block.class, method -> method.equals("getState") ? state : Material.BARREL);
        assertTrue(RaidService.isStorage(container));
    }
}
