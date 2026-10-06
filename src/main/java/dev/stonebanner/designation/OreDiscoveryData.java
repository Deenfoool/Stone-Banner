package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent de-duplication for ore veins exposed by Stone & Banner excavation.
 *
 * <p>The data is per-dimension because it is stored through {@link ServerLevel#getDataStorage()}.
 * Keeping discovered positions here prevents the same vein from generating a fresh notification
 * every time the world or chunk is reloaded.</p>
 */
public final class OreDiscoveryData extends SavedData {
    private static final String DATA_NAME = "stonebanner_ore_discoveries";
    private static final String TAG_REPORTED = "Reported";
    private static final int MAX_REPORTED_POSITIONS = 16_384;

    private final LinkedHashSet<Long> reportedPositions = new LinkedHashSet<>();

    public static OreDiscoveryData forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(
                OreDiscoveryData::load,
                OreDiscoveryData::new,
                DATA_NAME
        );
    }

    boolean isReported(BlockPos pos) {
        return pos != null && reportedPositions.contains(pos.asLong());
    }

    void markReported(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (BlockPos pos : positions) {
            if (pos != null && reportedPositions.add(pos.asLong())) {
                changed = true;
            }
        }

        while (reportedPositions.size() > MAX_REPORTED_POSITIONS) {
            Iterator<Long> iterator = reportedPositions.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
            changed = true;
        }

        if (changed) {
            setDirty();
        }
    }

    int reportedCount() {
        return reportedPositions.size();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        long[] values = reportedPositions.stream().mapToLong(Long::longValue).toArray();
        root.putLongArray(TAG_REPORTED, values);
        return root;
    }

    static OreDiscoveryData load(CompoundTag root) {
        OreDiscoveryData data = new OreDiscoveryData();
        for (long packed : root.getLongArray(TAG_REPORTED)) {
            data.reportedPositions.add(packed);
            if (data.reportedPositions.size() >= MAX_REPORTED_POSITIONS) {
                break;
            }
        }
        return data;
    }
}
