package dev.stonebanner.storage;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Community scope is territorial, never inferred from the container registration manager. */
public final class CitizenStorageAccess {
    private CitizenStorageAccess() {}

    public static boolean mayUse(HumanNpcEntity citizen, BlockPos container) {
        if (container == null || !(citizen.level() instanceof ServerLevel level)
                || !level.hasChunkAt(container)) return false;
        // Preserve the legacy shared network for companions and citizens without a camp.
        // Membership is authoritative SavedData, not an editable/display-only home string.
        return SettlementData.forLevel(level).residentHome(citizen.getUUID())
                .map(community -> community.contains(container)).orElse(true);
    }
}
