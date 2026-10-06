package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Bridges physical ItemEntity drops into persistent HAULING jobs without virtualizing the items. */
public final class DroppedItemHauling {
    public static final double SCAN_RADIUS = 1.75D;

    private DroppedItemHauling() {
    }

    public static boolean hasDroppedItems(ServerLevel level, BlockPos target) {
        return !itemsAt(level, target).isEmpty();
    }

    public static List<ItemStack> stacksAt(ServerLevel level, BlockPos target) {
        return itemsAt(level, target).stream().map(ItemEntity::getItem).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    /** Publish one location job; CitizenJobBoard deduplicates HAULING jobs at the same target. */
    public static void publishIfNeeded(ServerLevel level, BlockPos target) {
        if (level == null || target == null || !hasDroppedItems(level, target)) {
            return;
        }
        CitizenJobBoard.forLevel(level).publish(WorkType.HAULING, target, level.getGameTime());
    }

    /**
     * Moves as many real world drops as possible into marked haul-cargo slots.
     * Returns the number of physical items picked up; remainders stay in their original ItemEntity.
     */
    public static int collectInto(ServerLevel level, BlockPos target, CitizenInventory inventory) {
        if (level == null || target == null || inventory == null) {
            return 0;
        }
        int pickedUp = 0;
        for (ItemEntity itemEntity : itemsAt(level, target)) {
            ItemStack original = itemEntity.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remainder = inventory.addHaulCargo(original);
            int moved = original.getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            pickedUp += moved;
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
        return pickedUp;
    }

    private static List<ItemEntity> itemsAt(ServerLevel level, BlockPos target) {
        if (level == null || target == null || !level.hasChunkAt(target)) {
            return List.of();
        }
        return level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(target).inflate(SCAN_RADIUS),
                item -> item.isAlive() && !item.getItem().isEmpty()
        );
    }
}
