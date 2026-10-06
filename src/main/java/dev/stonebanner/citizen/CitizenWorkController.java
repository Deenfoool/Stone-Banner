package dev.stonebanner.citizen;

import dev.stonebanner.designation.ExcavationEgressSafety;
import dev.stonebanner.designation.ExcavationOreDiscovery;
import dev.stonebanner.designation.ExcavationPlanData;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Executes jobs chosen by CitizenJobPlanner.
 *
 * Work discovery is intentionally throttled. Physical job producers publish work to CitizenJobBoard;
 * this controller only reserves, travels, performs and completes a chosen job.
 */
public final class CitizenWorkController {
    private static final int ACQUIRE_INTERVAL_TICKS = 20;
    private static final int UNSAFE_EGRESS_RETRY_TICKS = 40;
    private static final double WORK_RANGE_SQR = 2.75D * 2.75D;
    private static final double FORESTRY_BASE_WORK = 60.0D;
    private static final double CLEARING_BASE_WORK = 20.0D;
    private static final double MINING_BASE_WORK_PER_HARDNESS = 40.0D;

    private final HumanNpcEntity owner;
    private CitizenJob currentJob;
    private WorkPhase phase = WorkPhase.IDLE;
    private int acquireCooldown;
    private double workProgress;

    public CitizenWorkController(HumanNpcEntity owner) {
        this.owner = owner;
    }

    public void tick() {
        if (owner.level().isClientSide || !(owner.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (currentJob == null) {
            tickAcquire(serverLevel);
            return;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(serverLevel);
        if (board.job(currentJob.id()).isEmpty()) {
            owner.commandController().stop();
            clearLocalState();
            return;
        }
        board.touch(currentJob.id(), owner.getUUID(), serverLevel.getGameTime());

        if (CitizenDecisionPolicy.isCriticalPreemption(owner.citizenData())) {
            interrupt(true);
            return;
        }

        if (!owner.citizenData().canTravelTo(currentJob.target())) {
            interrupt(true);
            return;
        }

        if (!isJobStillValid(serverLevel, currentJob)) {
            board.remove(currentJob.id());
            owner.commandController().stop();
            clearLocalState();
            return;
        }

        if (phase == WorkPhase.TRAVELLING) {
            tickTravelling(serverLevel);
        } else if (phase == WorkPhase.WORKING) {
            tickWorking(serverLevel);
        }
    }

    private void tickAcquire(ServerLevel level) {
        if (acquireCooldown > 0) {
            acquireCooldown--;
            return;
        }
        acquireCooldown = ACQUIRE_INTERVAL_TICKS;

        // Excavation is a job producer: only the currently exposed quarry/tunnel slice is published.
        // Reconciliation is globally throttled inside the SavedData, so many workers stay cheap.
        ExcavationPlanData.forLevel(level).reconcileIfDue(level);

        if (owner.commandController().hasActiveCommand()
                || owner.brainState() != CitizenBrainState.IDLE
                || CitizenDecisionPolicy.isCriticalPreemption(owner.citizenData())) {
            return;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        List<CitizenJob> candidates = new ArrayList<>();
        for (CitizenJob job : board.availableJobs(owner.getUUID(), level.getGameTime())) {
            if (!owner.citizenData().canTravelTo(job.target())) {
                continue;
            }
            if (!isJobStillValid(level, job)) {
                board.remove(job.id());
                continue;
            }
            if (findApproachPosition(level, job.target()).isEmpty()) {
                continue;
            }
            candidates.add(job);
        }

        Optional<CitizenJob> selected = CitizenJobPlanner.choose(owner.citizenData(), owner.blockPosition(), candidates);
        if (selected.isEmpty()) {
            return;
        }

        CitizenJob job = selected.get();
        if (!board.reserve(job.id(), owner.getUUID(), level.getGameTime())) {
            return;
        }

        BlockPos approach = findApproachPosition(level, job.target()).orElse(null);
        if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
            board.release(job.id(), owner.getUUID());
            return;
        }

        currentJob = job;
        phase = WorkPhase.TRAVELLING;
        workProgress = 0.0D;
    }

    private void tickTravelling(ServerLevel level) {
        if (isWithinWorkRange(currentJob.target())) {
            owner.commandController().stop();
            phase = WorkPhase.WORKING;
            owner.setBrainState(CitizenBrainState.WORK);
            return;
        }

        if (!owner.commandController().hasActiveCommand()) {
            BlockPos approach = findApproachPosition(level, currentJob.target()).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                interrupt(false);
            }
        }
    }

    private void tickWorking(ServerLevel level) {
        if (!isWithinWorkRange(currentJob.target())) {
            BlockPos approach = findApproachPosition(level, currentJob.target()).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                interrupt(false);
                return;
            }
            phase = WorkPhase.TRAVELLING;
            return;
        }

        owner.setBrainState(CitizenBrainState.WORK);
        owner.getLookControl().setLookAt(
                currentJob.target().getX() + 0.5D,
                currentJob.target().getY() + 0.5D,
                currentJob.target().getZ() + 0.5D
        );
        if (owner.tickCount % 10 == 0) {
            owner.swing(InteractionHand.MAIN_HAND);
        }

        workProgress += workRate(currentJob);
        if (workProgress < requiredWork(level, currentJob)) {
            return;
        }

        if (currentJob.workType() == WorkType.MINING) {
            if (isOccupiedByOtherCitizen(level, currentJob.target())) {
                return;
            }
            ExcavationPlanData plans = ExcavationPlanData.forLevel(level);
            if (!ExcavationEgressSafety.canSafelyMine(
                    level,
                    plans,
                    owner.blockPosition(),
                    currentJob.target()
            )) {
                deferUnsafeExcavation(level);
                return;
            }
        }
        completeCurrentJob(level);
    }

    private void deferUnsafeExcavation(ServerLevel level) {
        if (currentJob != null) {
            CitizenJobBoard.forLevel(level).release(currentJob.id(), owner.getUUID());
        }
        owner.commandController().stop();
        clearLocalState();
        owner.setBrainState(CitizenBrainState.IDLE);
        acquireCooldown = UNSAFE_EGRESS_RETRY_TICKS;
    }

    private void completeCurrentJob(ServerLevel level) {
        CitizenJob job = currentJob;
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        ExcavationPlanData excavationPlans = job.workType() == WorkType.MINING
                ? ExcavationPlanData.forLevel(level)
                : null;

        boolean completed = switch (job.workType()) {
            case FORESTRY, MINING, CLEARING -> level.destroyBlock(job.target(), true, owner);
            default -> false;
        };

        if (completed) {
            if (job.workType() == WorkType.MINING && excavationPlans != null) {
                ExcavationOreDiscovery.scanNewlyExposed(level, excavationPlans, job.target());
            }
            board.complete(job.id(), owner.getUUID());
        } else if (!isJobStillValid(level, job)) {
            board.remove(job.id());
        } else {
            board.release(job.id(), owner.getUUID());
        }

        clearLocalState();
        owner.setBrainState(CitizenBrainState.IDLE);
        acquireCooldown = 5;
    }

    public void interrupt(boolean stopMovement) {
        if (owner.level() instanceof ServerLevel serverLevel && currentJob != null) {
            CitizenJobBoard.forLevel(serverLevel).release(currentJob.id(), owner.getUUID());
        }
        clearLocalState();
        if (stopMovement) {
            owner.commandController().stop();
        }
    }

    public boolean hasActiveJob() {
        return currentJob != null;
    }

    public Optional<CitizenJob> currentJob() {
        return Optional.ofNullable(currentJob);
    }

    public WorkPhase phase() {
        return phase;
    }

    private boolean isWithinWorkRange(BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        return owner.distanceToSqr(center.x, center.y, center.z) <= WORK_RANGE_SQR;
    }

    private Optional<BlockPos> findApproachPosition(ServerLevel level, BlockPos target) {
        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = target.relative(direction);
            for (int yOffset : new int[]{0, -1, 1}) {
                BlockPos candidate = side.offset(0, yOffset, 0);
                if (BlockPathfinder.isWalkable(level, candidate)) {
                    candidates.add(candidate.immutable());
                }
            }
        }

        return candidates.stream()
                .min(Comparator.comparingDouble(pos -> owner.distanceToSqr(Vec3.atCenterOf(pos))));
    }

    private boolean isOccupiedByOtherCitizen(ServerLevel level, BlockPos target) {
        // Inflate slightly so an NPC standing exactly on the block's top face is treated as occupying it.
        AABB safetyVolume = new AABB(target).inflate(0.05D);
        return !level.getEntitiesOfClass(
                HumanNpcEntity.class,
                safetyVolume,
                npc -> npc != owner && npc.isAlive()
        ).isEmpty();
    }

    private static boolean isJobStillValid(ServerLevel level, CitizenJob job) {
        return WorkTargetRules.isValid(job.workType(), level, job.target());
    }

    private double workRate(CitizenJob job) {
        double healthEfficiency = owner.citizenData().health().workEfficiencyMultiplier();
        if (job.workType() == WorkType.MINING) {
            int skill = owner.citizenData().skill(CitizenSkill.MINING);
            return Math.max(0.20D, healthEfficiency * (1.0D + skill * 0.08D));
        }
        return Math.max(0.20D, healthEfficiency);
    }

    private static double requiredWork(ServerLevel level, CitizenJob job) {
        if (job.workType() == WorkType.FORESTRY) {
            return FORESTRY_BASE_WORK;
        }
        if (job.workType() == WorkType.CLEARING) {
            return CLEARING_BASE_WORK;
        }
        if (job.workType() == WorkType.MINING) {
            float hardness = level.getBlockState(job.target()).getDestroySpeed(level, job.target());
            return MINING_BASE_WORK_PER_HARDNESS * Math.max(1.0D, hardness);
        }
        return Double.POSITIVE_INFINITY;
    }

    private void clearLocalState() {
        currentJob = null;
        phase = WorkPhase.IDLE;
        workProgress = 0.0D;
    }

    public enum WorkPhase {
        IDLE,
        TRAVELLING,
        WORKING
    }
}
