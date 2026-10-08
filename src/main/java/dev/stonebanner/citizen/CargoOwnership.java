package dev.stonebanner.citizen;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.CitizenStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.UUID;

/** Persistent origin, independent of the carrier's current employer or membership. */
public final class CargoOwnership {
    public static final String DROP_OWNER = "SBProductionOwner";
    private CargoOwnership() {}

    public static UUID dropOwner(ItemEntity item) {
        return item.getPersistentData().hasUUID(DROP_OWNER) ? item.getPersistentData().getUUID(DROP_OWNER) : null;
    }

    public static void markDrop(ItemEntity item, UUID owner) {
        if (item != null && owner != null) item.getPersistentData().putUUID(DROP_OWNER, owner);
    }

    public static UUID workOwner(HumanNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        return SettlementData.forLevel(level).residentHome(npc.getUUID())
                .map(c -> c.owner()).orElseGet(() -> npc.citizenData().recruitedBy().orElse(null));
    }

    public static boolean mayDeliver(HumanNpcEntity npc, UUID owner, BlockPos store) {
        if (!CitizenStorageAccess.mayUse(npc, store)) return false;
        if (owner == null) return true; // Old saves retain their territorial/shared rules.
        var level = (ServerLevel) npc.level();
        var settlements = SettlementData.forLevel(level);
        if (!npc.citizenData().recruitedBy().map(owner::equals).orElse(true)) return false;
        var community = settlements.ownedBy(owner).orElse(null);
        if (community != null) return community.contains(store) && settlements.residentHome(npc.getUUID())
                .map(c -> c.id().equals(community.id())).orElse(false);
        return npc.citizenData().recruitedBy().map(owner::equals).orElse(true);
    }
}
