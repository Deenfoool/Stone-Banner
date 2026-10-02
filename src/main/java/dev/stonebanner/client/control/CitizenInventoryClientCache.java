package dev.stonebanner.client.control;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side snapshot cache populated only when the Citizen inspector requests inventory data. */
public final class CitizenInventoryClientCache {
    private static final Map<Integer, List<ItemStack>> SNAPSHOTS = new ConcurrentHashMap<>();

    private CitizenInventoryClientCache() {
    }

    public static void update(int entityId, List<ItemStack> stacks) {
        SNAPSHOTS.put(entityId, stacks.stream().map(ItemStack::copy).toList());
    }

    public static List<ItemStack> snapshot(int entityId) {
        return SNAPSHOTS.getOrDefault(entityId, List.of()).stream().map(ItemStack::copy).toList();
    }

    public static void clear(int entityId) {
        SNAPSHOTS.remove(entityId);
    }
}
