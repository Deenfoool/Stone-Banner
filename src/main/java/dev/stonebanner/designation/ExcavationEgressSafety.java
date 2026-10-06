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
 * removed or on the block directly above it. Removing a solid block can only reduce support, so a currently
 * valid route that does not use those nodes remains a safe egress route after destruction. The check is only
 * applied to mining jobs that belong to the currently exposed slice of an excavation plan.</p>
 */
public final class ExcavationEgressSafety {
    private static final int MAX_EGRESS_CANDIDATES = 16;

    private ExcavationEgressSafety() {
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
