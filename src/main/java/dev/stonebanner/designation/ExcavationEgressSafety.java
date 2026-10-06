package dev.stonebanner.designation;

import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Conservative pre-destruction safety check for active excavation plans.
 *
 * <p>The check intentionally uses the current world state and rejects a route if it relies on the block being
 * removed or on the block directly above it. Vertical quarries use their reserved perimeter ramp as the
 * long-distance egress invariant. Narrow shafts may instead use a continuous line of real vanilla ladders.
 * Tunnels validate a real route back through their entrance face.</p>
 */
public final class ExcavationEgressSafety {
    private static final int MAX_EGRESS_CANDIDATES = 16;

    private ExcavationEgressSafety() {
    }

    /** Server-authoritative reason why a plan can or cannot expose its current slice. */
    static ExcavationAccessStatus currentSliceAccessStatus(ServerLevel level, ExcavationPlanData.PlanView plan) {
        if (level == null || plan == null) {
            return ExcavationAccessStatus.NO_PATH;
        }
        if (plan.modeCode() != 0 || plan.currentSlice() == plan.maxY()) {
            return ExcavationAccessStatus.READY;
        }

        if (rampSupportAtY(plan, plan.currentSlice()) == null) {
            return hasCompleteLadderAccess(level, plan)
                    ? ExcavationAccessStatus.READY
                    : ExcavationAccessStatus.NEEDS_LADDER;
        }

        return rampChainIntact(level, plan, plan.currentSlice())
                ? ExcavationAccessStatus.READY
                : ExcavationAccessStatus.BLOCKED;
    }

    /** Controls whether the plan may publish work for its current slice and reports a transition once. */
    static boolean canExposeCurrentSlice(ServerLevel level, ExcavationPlanData.PlanView plan) {
        ExcavationAccessStatus status = currentSliceAccessStatus(level, plan);
        ExcavationAccessNotifier.update(level, plan, status);
        return status == ExcavationAccessStatus.READY;
    }

    public static boolean canSafelyMine(ServerLevel level, ExcavationPlanData plans,
                                        BlockPos workerFeet, BlockPos target) {
        if (level == null || plans == null || workerFeet == null || target == null) {
            return false;
        }

        Optional<ExcavationPlanData.PlanView> planResult = plans.activePlanFor(target);
        if (planResult.isEmpty()) {
            return true;
        }

        ExcavationPlanData.PlanView plan = planResult.get();
        if (!insideWorkVolume(plan, workerFeet)) {
            return true;
        }

        boolean standingOnLadderAboveTarget = workerFeet.equals(target.above())
                && level.getBlockState(workerFeet).is(Blocks.LADDER);
        if (!standingOnLadderAboveTarget && !routeSurvivesRemoval(workerFeet, target, List.of())) {
            ExcavationAccessNotifier.update(level, plan, ExcavationAccessStatus.BLOCKED);
            return false;
        }

        if (plan.modeCode() == 0) {
            ExcavationAccessStatus sliceStatus = currentSliceAccessStatus(level, plan);
            if (sliceStatus != ExcavationAccessStatus.READY) {
                ExcavationAccessNotifier.update(level, plan, sliceStatus);
                return false;
            }

            boolean safe = rampSupportAtY(plan, target.getY()) == null
                    ? canReachLadderExit(level, plan, workerFeet, target)
                    : canReachReservedRamp(level, plan, workerFeet, target);
            ExcavationAccessNotifier.update(
                    level,
                    plan,
                    safe ? ExcavationAccessStatus.READY : ExcavationAccessStatus.NO_PATH
            );
            return safe;
        }

        for (BlockPos egress : egressCandidates(level, plan, workerFeet)) {
            Optional<List<BlockPos>> route = BlockPathfinder.findPath(level, workerFeet, egress);
            if (route.isPresent() && routeSurvivesRemoval(workerFeet, target, route.get())) {
                ExcavationAccessNotifier.update(level, plan, ExcavationAccessStatus.READY);
                return true;
            }
        }
        ExcavationAccessNotifier.update(level, plan, ExcavationAccessStatus.NO_PATH);
        return false;
    }

    static boolean routeSurvivesRemoval(BlockPos workerFeet, BlockPos target, List<BlockPos> route) {
        BlockPos unsupportedFeet = target.above();
        if (workerFeet.equals(target) || workerFeet.equals(unsupportedFeet)) {
            return false;
        }
        for (BlockPos node : route) {
            if (node.equals(target) || node.equals(unsupportedFeet)) {
                return false;
            }
        }
        return true;
    }

    static List<BlockPos> rawEgressProbes(ExcavationPlanData.PlanView plan) {
        LinkedHashSet<BlockPos> probes = new LinkedHashSet<>();
        if (plan.modeCode() == 0) {
            int[] xs = samples(plan.minX(), plan.maxX());
            int[] zs = samples(plan.minZ(), plan.maxZ());
            int[] ys = {plan.maxY() + 1, plan.maxY() + 2, plan.maxY()};
            for (int y : ys) {
                for (int z : zs) {
                    probes.add(new BlockPos(plan.minX() - 1, y, z));
                    probes.add(new BlockPos(plan.maxX() + 1, y, z));
                }
                for (int x : xs) {
                    probes.add(new BlockPos(x, y, plan.minZ() - 1));
                    probes.add(new BlockPos(x, y, plan.maxZ() + 1));
                }
            }
        } else if (plan.modeCode() == 1) {
            int outsideX = plan.step() > 0 ? plan.minX() - 1 : plan.maxX() + 1;
            for (int y : samples(plan.minY(), plan.maxY() + 1)) {
                for (int z : samples(plan.minZ(), plan.maxZ())) {
                    probes.add(new BlockPos(outsideX, y, z));
                }
            }
        } else if (plan.modeCode() == 2) {
            int outsideZ = plan.step() > 0 ? plan.minZ() - 1 : plan.maxZ() + 1;
            for (int y : samples(plan.minY(), plan.maxY() + 1)) {
                for (int x : samples(plan.minX(), plan.maxX())) {
                    probes.add(new BlockPos(x, y, outsideZ));
                }
            }
        }
        return List.copyOf(probes);
    }

    /** Returns the reserved solid support for one quarry depth, or null for strips too narrow for a ramp. */
    static BlockPos rampSupportAtY(ExcavationPlanData.PlanView plan, int y) {
        int sizeX = plan.maxX() - plan.minX() + 1;
        int sizeZ = plan.maxZ() - plan.minZ() + 1;
        if (plan.modeCode() != 0 || sizeX < 2 || sizeZ < 2 || y < plan.minY() || y > plan.maxY()) {
            return null;
        }

        int perimeter = 2 * sizeX + 2 * sizeZ - 4;
        int depth = plan.maxY() - y;
        int index = Math.floorMod(depth, perimeter);

        if (index < sizeX) {
            return new BlockPos(plan.minX() + index, y, plan.minZ());
        }
        index -= sizeX;
        if (index < sizeZ - 1) {
            return new BlockPos(plan.maxX(), y, plan.minZ() + 1 + index);
        }
        index -= sizeZ - 1;
        if (index < sizeX - 1) {
            return new BlockPos(plan.maxX() - 1 - index, y, plan.maxZ());
        }
        index -= sizeX - 1;
        return new BlockPos(plan.minX(), y, plan.maxZ() - 1 - index);
    }

    /** Deterministic ladder column used by narrow shafts until explicit access placement UI is added. */
    static BlockPos ladderAccessAtY(ExcavationPlanData.PlanView plan, int y) {
        return new BlockPos(plan.minX(), y, plan.minZ());
    }

    /** Number of already-open shaft cells that require ladders before the current slice can be worked. */
    static int requiredLadderCount(ExcavationPlanData.PlanView plan) {
        if (plan == null || plan.modeCode() != 0 || plan.currentSlice() >= plan.maxY()) {
            return 0;
        }
        return plan.maxY() - plan.currentSlice();
    }

    private static boolean hasCompleteLadderAccess(ServerLevel level, ExcavationPlanData.PlanView plan) {
        int required = requiredLadderCount(plan);
        if (required <= 0) {
            return true;
        }
        for (int y = plan.maxY(); y > plan.currentSlice(); y--) {
            BlockPos ladder = ladderAccessAtY(plan, y);
            if (!level.hasChunkAt(ladder) || !level.getBlockState(ladder).is(Blocks.LADDER)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReachLadderExit(ServerLevel level, ExcavationPlanData.PlanView plan,
                                              BlockPos workerFeet, BlockPos target) {
        if (!hasCompleteLadderAccess(level, plan)) {
            return false;
        }

        BlockPos landing = ladderAccessAtY(plan, plan.currentSlice() + 1);
        if (workerFeet.equals(landing)) {
            return level.getBlockState(landing).is(Blocks.LADDER);
        }

        Optional<List<BlockPos>> route = BlockPathfinder.findPath(level, workerFeet, landing);
        if (route.isEmpty()) {
            return false;
        }
        for (BlockPos node : route.get()) {
            if (node.equals(target)) {
                return false;
            }
            if (node.equals(target.above()) && !level.getBlockState(node).is(Blocks.LADDER)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReachReservedRamp(ServerLevel level, ExcavationPlanData.PlanView plan,
                                                BlockPos workerFeet, BlockPos target) {
        BlockPos currentSupport = rampSupportAtY(plan, target.getY());
        if (currentSupport == null || !rampChainIntact(level, plan, target.getY())) {
            return false;
        }

        BlockPos currentLanding = currentSupport.above();
        Optional<List<BlockPos>> route = BlockPathfinder.findPath(level, workerFeet, currentLanding);
        return route.isPresent() && routeSurvivesRemoval(workerFeet, target, route.get());
    }

    private static boolean rampChainIntact(ServerLevel level, ExcavationPlanData.PlanView plan, int fromY) {
        BlockPos previous = null;
        for (int y = plan.maxY(); y >= fromY; y--) {
            BlockPos support = rampSupportAtY(plan, y);
            if (support == null || !level.hasChunkAt(support)
                    || level.getBlockState(support).getCollisionShape(level, support).isEmpty()
                    || !BlockPathfinder.isWalkable(level, support.above())) {
                return false;
            }
            if (previous != null) {
                int horizontalStep = Math.abs(previous.getX() - support.getX())
                        + Math.abs(previous.getZ() - support.getZ());
                if (horizontalStep != 1 || previous.getY() != support.getY() + 1) {
                    return false;
                }
            }
            previous = support;
        }
        return true;
    }

    private static List<BlockPos> egressCandidates(ServerLevel level, ExcavationPlanData.PlanView plan,
                                                    BlockPos workerFeet) {
        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (BlockPos probe : rawEgressProbes(plan)) {
            if (level.hasChunkAt(probe) && BlockPathfinder.isWalkable(level, probe)) {
                candidates.add(probe.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> distanceSquared(workerFeet, pos)));
        if (candidates.size() > MAX_EGRESS_CANDIDATES) {
            return List.copyOf(candidates.subList(0, MAX_EGRESS_CANDIDATES));
        }
        return List.copyOf(candidates);
    }

    private static boolean insideWorkVolume(ExcavationPlanData.PlanView plan, BlockPos feet) {
        return feet.getX() >= plan.minX() && feet.getX() <= plan.maxX()
                && feet.getZ() >= plan.minZ() && feet.getZ() <= plan.maxZ()
                && feet.getY() >= plan.minY() && feet.getY() <= plan.maxY() + 1;
    }

    private static double distanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    private static int[] samples(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        values.add(low);
        values.add(high);
        values.add(low + (high - low) / 2);
        values.add(low + (high - low) / 4);
        values.add(low + ((high - low) * 3) / 4);
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
