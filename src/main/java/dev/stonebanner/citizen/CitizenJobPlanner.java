package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Pure deterministic job selector. It never scans the world itself; producers publish jobs and the planner
 * only ranks candidates. That keeps heavy discovery work out of every Citizen tick.
 */
public final class CitizenJobPlanner {
    private CitizenJobPlanner() {
    }

    public static Optional<CitizenJob> choose(CitizenData data, BlockPos origin, Collection<CitizenJob> jobs) {
        if (data == null || origin == null || jobs == null || jobs.isEmpty()) {
            return Optional.empty();
        }

        return jobs.stream()
                .filter(job -> job != null && job.canBeDoneBy(data))
                .min(Comparator
                        .comparingInt((CitizenJob job) -> priorityRank(data.workPriority(job.workType())))
                        .thenComparingLong(job -> squaredDistance(origin, job.target()))
                        .thenComparingLong(CitizenJob::createdTick)
                        .thenComparingLong(CitizenJob::id));
    }

    private static int priorityRank(WorkPriority priority) {
        return priority == WorkPriority.DISABLED ? Integer.MAX_VALUE : priority.code();
    }

    private static long squaredDistance(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
