package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Detects ore veins newly exposed just outside an active excavation volume. */
public final class ExcavationOreDiscovery {
    private static final int MAX_VEIN_SCAN = 256;

    private ExcavationOreDiscovery() {
    }

    /**
     * Scans the six faces exposed by one successfully mined excavation block.
     * Ores inside the designation are ignored because they are already part of the planned work.
     * Ores outside the plan are reported once, but never turned into mining jobs automatically.
     */
    public static int scanNewlyExposed(ServerLevel level, ExcavationPlanData plans, BlockPos minedTarget) {
        if (level == null || plans == null || minedTarget == null) {
            return 0;
        }

        Optional<ExcavationPlanData.PlanView> planResult = plans.activePlanFor(minedTarget);
        if (planResult.isEmpty()) {
            return 0;
        }

        ExcavationPlanData.PlanView plan = planResult.get();
        OreDiscoveryData discoveries = OreDiscoveryData.forLevel(level);
        int notifications = 0;

        for (Direction direction : Direction.values()) {
            BlockPos exposed = minedTarget.relative(direction);
            if (insidePlan(plan, exposed)
                    || !level.hasChunkAt(exposed)
                    || !isOre(level.getBlockState(exposed))) {
                continue;
            }

            Set<BlockPos> vein = collectVein(level, plan, exposed);
            if (vein.isEmpty()) {
                continue;
            }

            boolean knownVein = vein.stream().anyMatch(discoveries::isReported);
            discoveries.markReported(vein);
            if (knownVein) {
                continue;
            }

            BlockState state = level.getBlockState(exposed);
            Component message = Component.translatable(
                    "message.stonebanner.ore_discovered",
                    state.getBlock().getName(),
                    exposed.getX(),
                    exposed.getY(),
                    exposed.getZ(),
                    vein.size()
            );
            level.players().forEach(player -> player.sendSystemMessage(message));
            notifications++;
        }
        return notifications;
    }

    static boolean insidePlan(ExcavationPlanData.PlanView plan, BlockPos pos) {
        return pos.getX() >= plan.minX() && pos.getX() <= plan.maxX()
                && pos.getY() >= plan.minY() && pos.getY() <= plan.maxY()
                && pos.getZ() >= plan.minZ() && pos.getZ() <= plan.maxZ();
    }

    private static Set<BlockPos> collectVein(ServerLevel level, ExcavationPlanData.PlanView plan, BlockPos seed) {
        BlockState seedState = level.getBlockState(seed);
        if (!isOre(seedState)) {
            return Set.of();
        }

        Block seedBlock = seedState.getBlock();
        LinkedHashSet<BlockPos> found = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable());

        while (!queue.isEmpty() && found.size() < MAX_VEIN_SCAN) {
            BlockPos current = queue.removeFirst();
            if (found.contains(current)
                    || insidePlan(plan, current)
                    || !level.hasChunkAt(current)) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (state.getBlock() != seedBlock || !isOre(state)) {
                continue;
            }

            found.add(current.immutable());
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!found.contains(next)) {
                    queue.addLast(next.immutable());
                }
            }
        }
        return Set.copyOf(found);
    }

    private static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES);
    }
}
