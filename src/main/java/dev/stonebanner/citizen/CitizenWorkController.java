package dev.stonebanner.citizen;

import dev.stonebanner.designation.ExcavationEgressSafety;
import dev.stonebanner.designation.ExcavationOreDiscovery;
import dev.stonebanner.designation.ExcavationPlanData;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Executes jobs chosen by CitizenJobPlanner and physical storage delivery.
 *
 * <p>Physical work creates real ItemEntity drops. A worker with HAULING enabled may pick up its fresh drops;
 * any remainder becomes a persistent HAULING job that another Citizen can collect. Carried work cargo lives
 * in marked slots of the Citizen's real persistent inventory and is physically delivered to registered storage.</p>
 */
public final class CitizenWorkController {
    private static final int ACQUIRE_INTERVAL_TICKS = 20;
    private static final int UNSAFE_EGRESS_RETRY_TICKS = 40;
    private static final int CARGO_RETRY_TICKS = 40;
    private static final double WORK_RANGE_SQR = 2.75D * 2.75D;
    private static final double STORAGE_SEARCH_RADIUS = 64.0D;
    private static final double FORESTRY_BASE_WORK = 60.0D;
    private static final double CLEARING_BASE_WORK = 20.0D;
    private static final double HAUL_PICKUP_WORK = 6.0D;
    private static final double MINING_BASE_WORK_PER_HARDNESS = 40.0D;

    private final HumanNpcEntity owner;
    private CitizenJob currentJob;
    private WorkPhase phase = WorkPhase.IDLE;
    private int acquireCooldown;
    private double workProgress;

    private CargoPhase cargoPhase = CargoPhase.IDLE;
    private BlockPos cargoStorageTarget;
    private int cargoRetryCooldown;

    public CitizenWorkController(HumanNpcEntity owner) {
        this.owner = owner;
    }

    public void tick() {
        if (owner.level().isClientSide || !(owner.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (currentJob == null && owner.citizenData().inventory().hasHaulCargo()) {
            tickCargoDelivery(serverLevel);
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
            if (!isJobActionable(level, job)) {
                continue;
            }
            if (findJobApproachPosition(level, job).isEmpty()) {
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

        BlockPos approach = findJobApproachPosition(level, job).orElse(null);
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
            BlockPos approach = findJobApproachPosition(level, currentJob).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                interrupt(false);
            }
        }
    }

    private void tickWorking(ServerLevel level) {
        if (!isWithinWorkRange(currentJob.target())) {
            BlockPos approach = findJobApproachPosition(level, currentJob).orElse(null);
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

        if (currentJob.workType() == WorkType.HAULING) {
            completeHaulingPickup(level);
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

    private void tickCargoDelivery(ServerLevel level) {
        CitizenInventory inventory = owner.citizenData().inventory();
        if (!inventory.hasHaulCargo()) {
            resetCargoDelivery();
            owner.setBrainState(CitizenBrainState.IDLE);
            return;
        }

        if (CitizenDecisionPolicy.isCriticalPreemption(owner.citizenData())) {
            if (cargoPhase == CargoPhase.TRAVELLING) {
                owner.commandController().stop();
            }
            cargoPhase = CargoPhase.WAITING_STORAGE;
            cargoStorageTarget = null;
            return;
        }

        if (cargoRetryCooldown > 0) {
            cargoRetryCooldown--;
            return;
        }

        if (cargoStorageTarget == null) {
            CitizenInventory.HaulCargo cargo = inventory.firstHaulCargo().orElse(null);
            if (cargo == null) {
                resetCargoDelivery();
                return;
            }

            StorageData storage = StorageData.forLevel(level);
            BlockPos storageTarget = storage.nearestAcceptingContainer(
                    level,
                    owner.blockPosition(),
                    cargo.stack(),
                    STORAGE_SEARCH_RADIUS
            ).orElse(null);
            if (storageTarget == null || !owner.citizenData().canTravelTo(storageTarget)) {
                cargoPhase = CargoPhase.WAITING_STORAGE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
                owner.setBrainState(CitizenBrainState.IDLE);
                return;
            }

            BlockPos approach = findApproachPosition(level, storageTarget).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                cargoPhase = CargoPhase.WAITING_STORAGE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
                owner.setBrainState(CitizenBrainState.IDLE);
                return;
            }

            cargoStorageTarget = storageTarget.immutable();
            cargoPhase = CargoPhase.TRAVELLING;
            owner.setBrainState(CitizenBrainState.WORK);
            return;
        }

        if (cargoPhase == CargoPhase.TRAVELLING) {
            if (isWithinWorkRange(cargoStorageTarget)) {
                owner.commandController().stop();
                cargoPhase = CargoPhase.DEPOSITING;
            } else if (!owner.commandController().hasActiveCommand()) {
                cargoStorageTarget = null;
                cargoPhase = CargoPhase.WAITING_STORAGE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
            }
            return;
        }

        if (cargoPhase == CargoPhase.DEPOSITING) {
            depositCargo(level, cargoStorageTarget);
            if (!inventory.hasHaulCargo()) {
                resetCargoDelivery();
                owner.setBrainState(CitizenBrainState.IDLE);
                acquireCooldown = 5;
                return;
            }
            cargoStorageTarget = null;
            cargoPhase = CargoPhase.WAITING_STORAGE;
            cargoRetryCooldown = CARGO_RETRY_TICKS;
        }
    }

    private void depositCargo(ServerLevel level, BlockPos storageTarget) {
        StorageData storage = StorageData.forLevel(level);
        CitizenInventory inventory = owner.citizenData().inventory();
        for (CitizenInventory.HaulCargo cargo : inventory.haulCargoSnapshot()) {
            ItemStack remainder = storage.insertAt(level, storageTarget, cargo.stack());
            inventory.setHaulCargoStack(cargo.slot(), remainder);
        }
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
        Set<UUID> itemEntitiesBefore = nearbyItemEntityIds(level, job.target());

        boolean completed = switch (job.workType()) {
            case FORESTRY, MINING, CLEARING -> level.destroyBlock(job.target(), true, owner);
            default -> false;
        };

        if (completed) {
            if (owner.citizenData().workPriority(WorkType.HAULING) != WorkPriority.DISABLED) {
                collectNewWorkDrops(level, job.target(), itemEntitiesBefore);
            }
            DroppedItemHauling.publishIfNeeded(level, job.target());
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

    private void completeHaulingPickup(ServerLevel level) {
        CitizenJob job = currentJob;
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        int pickedUp = DroppedItemHauling.collectInto(level, job.target(), owner.citizenData().inventory());
        if (pickedUp > 0) {
            board.complete(job.id(), owner.getUUID());
            DroppedItemHauling.publishIfNeeded(level, job.target());
        } else if (!DroppedItemHauling.hasDroppedItems(level, job.target())) {
            board.remove(job.id());
        } else {
            board.release(job.id(), owner.getUUID());
        }
        clearLocalState();
        owner.setBrainState(CitizenBrainState.IDLE);
        acquireCooldown = 5;
    }

    private Set<UUID> nearbyItemEntityIds(ServerLevel level, BlockPos target) {
        HashSet<UUID> ids = new HashSet<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(target).inflate(DroppedItemHauling.SCAN_RADIUS),
                ItemEntity::isAlive
        )) {
            ids.add(itemEntity.getUUID());
        }
        return ids;
    }

    private void collectNewWorkDrops(ServerLevel level, BlockPos target, Set<UUID> itemEntitiesBefore) {
        CitizenInventory inventory = owner.citizenData().inventory();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(target).inflate(DroppedItemHauling.SCAN_RADIUS),
                ItemEntity::isAlive
        )) {
            if (itemEntitiesBefore.contains(itemEntity.getUUID())) {
                continue;
            }
            ItemStack original = itemEntity.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remainder = inventory.addHaulCargo(original);
            if (remainder.getCount() == original.getCount()) {
                continue;
            }
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
    }

    public void interrupt(boolean stopMovement) {
        if (owner.level() instanceof ServerLevel serverLevel && currentJob != null) {
            CitizenJobBoard.forLevel(serverLevel).release(currentJob.id(), owner.getUUID());
        }
        clearLocalState();
        resetCargoDelivery();
        if (stopMovement) {
            owner.commandController().stop();
        }
    }

    public boolean hasActiveJob() {
        return currentJob != null || cargoPhase != CargoPhase.IDLE;
    }

    public Optional<CitizenJob> currentJob() {
        return Optional.ofNullable(currentJob);
    }

    public Optional<WorkType> activeWorkType() {
        if (currentJob != null) {
            return Optional.of(currentJob.workType());
        }
        if (owner.citizenData().inventory().hasHaulCargo()) {
            return Optional.of(WorkType.HAULING);
        }
        return Optional.empty();
    }

    public WorkPhase phase() {
        return phase;
    }

    private boolean isWithinWorkRange(BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        return owner.distanceToSqr(center.x, center.y, center.z) <= WORK_RANGE_SQR;
    }

    private Optional<BlockPos> findJobApproachPosition(ServerLevel level, CitizenJob job) {
        if (job.workType() == WorkType.HAULING && BlockPathfinder.isWalkable(level, job.target())) {
            return Optional.of(job.target().immutable());
        }
        return findApproachPosition(level, job.target());
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

    private boolean isJobActionable(ServerLevel level, CitizenJob job) {
        if (job.workType() != WorkType.HAULING) {
            return true;
        }
        ItemStack firstDrop = DroppedItemHauling.firstStack(level, job.target()).orElse(null);
        if (firstDrop == null) {
            return false;
        }
        BlockPos storageTarget = StorageData.forLevel(level).nearestAcceptingContainer(
                level,
                job.target(),
                firstDrop,
                STORAGE_SEARCH_RADIUS
        ).orElse(null);
        return storageTarget != null
                && owner.citizenData().canTravelTo(storageTarget)
                && findApproachPosition(level, storageTarget).isPresent();
    }

    private static boolean isJobStillValid(ServerLevel level, CitizenJob job) {
        if (job.workType() == WorkType.HAULING) {
            return DroppedItemHauling.hasDroppedItems(level, job.target());
        }
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
        if (job.workType() == WorkType.HAULING) {
            return HAUL_PICKUP_WORK;
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

    private void resetCargoDelivery() {
        cargoPhase = CargoPhase.IDLE;
        cargoStorageTarget = null;
        cargoRetryCooldown = 0;
    }

    public enum WorkPhase {
        IDLE,
        TRAVELLING,
        WORKING
    }

    private enum CargoPhase {
        IDLE,
        WAITING_STORAGE,
        TRAVELLING,
        DEPOSITING
    }
}
