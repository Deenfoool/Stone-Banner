package dev.stonebanner.designation;

import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
 * long-distance egress invariant, so deep quarries are not limited by the normal short-range A* vertical bound.
 * Tunnels still validate a real route back through their entrance face. The check is only applied to mining
 * jobs that belong to the currently exposed slice of an excavation plan.</p>
 */
public final class ExcavationEgressSafety {
    private static final int MAX_EGRESS_CANDIDATES = 16;

    private ExcavationEgressSafety() {
    }

    /**
     * Controls whether the plan may publish work for its current slice.
     * The top quarry layer is always allowed; every deeper layer requires the preserved ramp to remain intact.
     * Tunnel sequencing already advances from its entrance, so its per-block egress guard remains authoritative.
     */
    static boolean canExposeCurrentSlice(ServerLevel level, ExcavationPlanData.PlanView plan) {
        if (level == null || plan == null) {
            return false;
        }
        if (plan.modeCode() != 0) {
            return true;
        }
        if (plan.currentSlice() == plan.maxY()) {
            return true;
        }
        return rampChainIntact(level, plan, plan.currentSlice());
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

        if (!routeSurvivesRemoval(workerFeet, target, List.of())) {
            return false;
        }

        if (plan.modeCode() == 0) {
            return canReachReservedRamp(level, plan, workerFeet, target);
        }

        for (BlockPos egress : egressCandidates(level, plan, workerFeet)) {
            Optional<List<BlockPos>> route = BlockPathfinder.findPath(level, workerFeet, egress);
            if (route.isPresent() && routeSurvivesRemoval(workerFeet, target, route.get())) {
                return true;
            }
        }
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
