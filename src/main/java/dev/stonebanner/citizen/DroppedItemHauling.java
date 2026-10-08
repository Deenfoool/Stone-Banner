package dev.stonebanner.citizen;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.CitizenStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    public static List<ItemStack> stacksAt(ServerLevel level, BlockPos target, HumanNpcEntity citizen) {
        return itemsAt(level, target).stream().filter(item -> mayCollect(citizen, item))
                .map(ItemEntity::getItem).map(ItemStack::copy).toList();
    }

    /** Pickup rights are checked on each entity, not on the shared job's location. */
    public static boolean mayCollect(HumanNpcEntity citizen, ItemEntity item) {
        if (citizen == null || item == null || citizen.level() != item.level()
                || !item.isAlive() || item.getItem().isEmpty()
                || !(citizen.level() instanceof ServerLevel level)
                || !CitizenStorageAccess.mayUse(citizen, item.blockPosition())) return false;
        // Vanilla target ownership is exclusive. Thrower alone is not a pickup restriction.
        // 1.20.1 getOwner() resolves the thrower, NOT the private pickup target.
        // Read its vanilla save field without reflection/access transformers or entity mutation.
        var vanilla = new CompoundTag();
        item.addAdditionalSaveData(vanilla);
        if (vanilla.hasUUID("Owner") && !vanilla.getUUID("Owner").equals(citizen.getUUID())) return false;
        if (!item.getPersistentData().hasUUID("SBProductionOwner")) return true;
        var productionOwner = item.getPersistentData().getUUID("SBProductionOwner");
        if (!citizen.citizenData().recruitedBy().map(productionOwner::equals).orElse(true)) return false;
        var settlements = SettlementData.forLevel(level);
        var community = settlements.ownedBy(productionOwner).orElse(null);
        if (community != null) return settlements.residentHome(citizen.getUUID())
                .map(home -> home.id().equals(community.id())).orElse(false);
        // Unaffiliated citizens may share ordinary drops, but not another player's tagged output.
        return citizen.citizenData().recruitedBy().map(productionOwner::equals).orElse(false);
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
    public static int collectInto(ServerLevel level, BlockPos target, HumanNpcEntity citizen) {
        if (level == null || target == null || citizen == null || citizen.level() != level) {
            return 0;
        }
        CitizenInventory inventory = citizen.citizenData().inventory();
        int pickedUp = 0;
        for (ItemEntity itemEntity : itemsAt(level, target)) {
            if (!mayCollect(citizen, itemEntity)) continue;
            ItemStack original = itemEntity.getItem();
            if (original.isEmpty()) {
                continue;
            }
            var cargoOwner = CargoOwnership.dropOwner(itemEntity);
            if (cargoOwner == null) cargoOwner = CargoOwnership.workOwner(citizen);
            ItemStack remainder = inventory.addHaulCargo(original, cargoOwner);
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
