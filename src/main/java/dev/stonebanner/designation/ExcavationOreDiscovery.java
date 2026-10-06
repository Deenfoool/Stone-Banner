package dev.stonebanner.designation;

import dev.stonebanner.network.OreDiscoverySyncEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.Tags;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Discovers only physically exposed ore; permission never extends a player's excavation volume. */
public final class ExcavationOreDiscovery {
    private ExcavationOreDiscovery() {}

    public static int scanNewlyExposed(ServerLevel level, ExcavationPlanData plans, BlockPos minedTarget) {
        if (level == null || plans == null || minedTarget == null || plans.activePlanFor(minedTarget).isEmpty()) return 0;
        int changed = 0;
        for (Direction direction : Direction.values()) {
            if (discover(level, minedTarget.relative(direction))) changed++;
        }
        if (changed > 0) OreDiscoverySyncEvents.broadcast(level);
        return changed;
    }

    /** Called before an ore job can execute, including ore already inside the designation. */
    public static boolean mayMine(ServerLevel level, BlockPos target) {
        if (!level.getBlockState(target).is(Tags.Blocks.ORES)) return true;
        if (discover(level, target)) OreDiscoverySyncEvents.broadcast(level);
        return OreDiscoveryData.forLevel(level).at(target).map(OreDiscoveryData.Finding::approved).orElse(false);
    }

    private static boolean discover(ServerLevel level, BlockPos seed) {
        if (!level.hasChunkAt(seed) || !level.getBlockState(seed).is(Tags.Blocks.ORES) || !exposed(level, seed)) return false;
        OreDiscoveryData data = OreDiscoveryData.forLevel(level);
        // Existing cells have already been scanned; newly opened neighbours are separate seeds.
        if (data.at(seed).isPresent()) return false;
        String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(seed).getBlock()).toString();
        Set<BlockPos> vein = collectExposed(seed, blockId,
                pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(Tags.Blocks.ORES)
                        ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : "",
                pos -> exposed(level, pos));
        OreDiscoveryData.Finding finding = data.record(blockId, vein);
        if (finding == null) return false;
        return true;
    }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            var state = level.getBlockState(neighbor);
            // A ladder shaft or water also exposes a face; neither is an opaque rock wall.
            if (state.getCollisionShape(level, neighbor).isEmpty()
                    && !state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Bounded flood fill stops at unexposed rock; it cannot reveal a concealed continuation. */
    static Set<BlockPos> collectExposed(BlockPos seed, String blockId,
                                       Function<BlockPos, String> blocks, Predicate<BlockPos> exposed) {
        LinkedHashSet<BlockPos> found = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(seed);
        while (!queue.isEmpty() && found.size() < OreDiscoveryData.MAX_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || !blockId.equals(blocks.apply(pos)) || !exposed.test(pos)) continue;
            found.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
        return Collections.unmodifiableSet(found);
    }
}
