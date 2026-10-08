package dev.stonebanner.citizen;

import dev.stonebanner.designation.ExcavationEgressSafety;
import dev.stonebanner.designation.ExcavationLadderAutomation;
import dev.stonebanner.designation.ExcavationLadderTaskData;
import dev.stonebanner.designation.ExcavationOreDiscovery;
import dev.stonebanner.designation.OreDiscoveryData;
import dev.stonebanner.designation.ExcavationPlanData;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.storage.StorageData;
import dev.stonebanner.storage.CitizenStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * Executes jobs chosen by CitizenJobPlanner, physical storage delivery and excavation support work.
 *
 * <p>Physical work creates real ItemEntity drops. A worker with HAULING enabled may pick up its fresh drops;
 * any remainder becomes a persistent HAULING job that another Citizen can collect. Carried work cargo lives
 * in marked slots of the Citizen's real persistent inventory and is physically delivered to registered storage.
 * Narrow-shaft ladder BUILDING jobs likewise fetch a real ladder from registered storage before placement.</p>
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
    private final dev.stonebanner.production.CitizenProductionController production;
    private final dev.stonebanner.construction.CitizenConstructionController construction;
    private CitizenJob currentJob;
    private WorkPhase phase = WorkPhase.IDLE;
    private int acquireCooldown;
    private double workProgress;

    private WorkBlockReason blockReason = WorkBlockReason.NONE;
    private long blockReasonUntil;
    public WorkBlockReason blockReason() { return blockReason; }
    public void clearBlockReason() { blockReason = WorkBlockReason.NONE; blockReasonUntil = 0; }
    private void blocked(WorkBlockReason reason) { blockReason = reason; blockReasonUntil = owner.level().getGameTime() + 120; }
    private CargoPhase cargoPhase = CargoPhase.IDLE;
    private dev.stonebanner.storage.DeliveryStatus deliveryStatus = dev.stonebanner.storage.DeliveryStatus.IDLE;
    private final java.util.Map<BlockPos, Long> failedCargoStorages = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Optional<BlockPos>> storageApproachCache = new java.util.HashMap<>();
    private long storageRouteTick = Long.MIN_VALUE;
    private int storageRouteBudget;
    private record DeliveryRoute(BlockPos storage, BlockPos approach) {}
    public dev.stonebanner.storage.DeliveryStatus deliveryStatus() { return deliveryStatus; }
    private BlockPos cargoStorageTarget;
    private int cargoRetryCooldown;

    private LadderBuildPhase ladderBuildPhase = LadderBuildPhase.IDLE;
    private BlockPos ladderStorageTarget;

    public CitizenWorkController(HumanNpcEntity owner) {
        this.owner = owner;
        production = new dev.stonebanner.production.CitizenProductionController(owner);
        construction = new dev.stonebanner.construction.CitizenConstructionController(owner);
    }

    public void tick() {
        if (owner.level().isClientSide || !(owner.level() instanceof ServerLevel serverLevel) || !owner.isAlive()) {
            return;
        }

        if (currentJob == null && serverLevel.getGameTime() >= blockReasonUntil) clearBlockReason();
        // Player's queued orders take precedence even between two sequential waypoints.
        // A queued WORK item owns currentJob and is allowed to progress normally.
        if (currentJob == null && owner.orderSequence().hasOrders()) return;
        if(owner.brainState()==CitizenBrainState.SLEEP){if(hasActiveJob())interrupt(true);owner.setBrainState(CitizenBrainState.SLEEP);return;}
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
        // A lease can be reassigned while this entity's chunk is unloaded.
        if (!board.touch(currentJob.id(), owner.getUUID(), serverLevel.getGameTime())
                || !currentJob.canBeDoneBy(owner.citizenData())) {
            interrupt(true);
            return;
        }

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

        if (isConstructionJob(serverLevel,currentJob)) {
            var result=construction.tick(currentJob);
            phase=construction.working()?WorkPhase.WORKING:WorkPhase.TRAVELLING;
            if(result!=dev.stonebanner.construction.CitizenConstructionController.Result.RUNNING){
                var why=construction.reason();
                if(result==dev.stonebanner.construction.CitizenConstructionController.Result.COMPLETE)board.complete(currentJob.id(),owner.getUUID());
                else board.release(currentJob.id(),owner.getUUID());
                owner.commandController().stop();clearLocalState();acquireCooldown=40;
                if(why!=WorkBlockReason.NONE)blocked(why);
            }
            return;
        }
        if (isProductionJob(currentJob)) {
            var result=production.tick(currentJob);
            phase=production.working()?WorkPhase.WORKING:WorkPhase.TRAVELLING;
            if(result!=dev.stonebanner.production.CitizenProductionController.Result.RUNNING){
                var why=production.reason();
                if(result==dev.stonebanner.production.CitizenProductionController.Result.COMPLETE){
                    owner.citizenData().practice(CitizenSkillRules.skillFor(currentJob.workType()),5);
                    board.complete(currentJob.id(),owner.getUUID());
                }else board.release(currentJob.id(),owner.getUUID());
                owner.commandController().stop();clearLocalState();acquireCooldown=40;
                if(why!=WorkBlockReason.NONE)blocked(why);
            }
            return;
        }
        if (isLadderBuildJob(serverLevel, currentJob)) {
            tickLadderBuild(serverLevel);
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

        ExcavationPlanData.forLevel(level).reconcileIfDue(level);

        if (owner.commandController().hasActiveCommand()
                || owner.brainState() != CitizenBrainState.IDLE
                || CitizenDecisionPolicy.isCriticalPreemption(owner.citizenData())) {
            return;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        List<CitizenJob> candidates = new ArrayList<>();
        for (CitizenJob job : board.availableJobs(owner.getUUID(), level.getGameTime())) {
            if (!job.canBeDoneBy(owner.citizenData())) continue;
            if (!owner.citizenData().canTravelTo(job.target())) {
                continue;
            }
            if (!isJobStillValid(level, job)) {
                board.remove(job.id());
                continue;
            }
            if (job.workType() == WorkType.MINING
                    && ExcavationPlanData.forLevel(level).containsActiveTarget(job.target())
                    && OreDiscoveryData.forLevel(level).at(job.target())
                        .map(finding -> !finding.approved()).orElse(false)) {
                blocked(WorkBlockReason.ORE_PERMISSION);
                continue;
            }
            if (!isJobActionable(level, job)) {
                continue;
            }
            if (!isConstructionJob(level,job) && !isLadderBuildJob(level, job) && findJobApproachPosition(level, job).isEmpty()) {
                blocked(WorkBlockReason.NO_PATH);
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

        if (isConstructionJob(level,job)) {
            clearBlockReason();currentJob=job;phase=WorkPhase.TRAVELLING;workProgress=0;return;
        }
        if (isLadderBuildJob(level, job)) {
            if (!beginLadderBuild(level, job)) {
                board.release(job.id(), owner.getUUID());
            }
            return;
        }

        BlockPos approach = findJobApproachPosition(level, job).orElse(null);
        if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
            board.release(job.id(), owner.getUUID());
            blocked(WorkBlockReason.NO_PATH);
            return;
        }

        clearBlockReason();
        currentJob = job;
        phase = WorkPhase.TRAVELLING;
        workProgress = 0.0D;
    }

    /** Explicit context order still respects skills, safety, travel and reservations. */
    public boolean assign(CitizenJob job) {
        if (owner.citizenData().returningToVillage()) return false;
        if (!owner.isAlive() || !owner.citizenData().health().canMoveIndependently()) return false;
        if(!(owner.level() instanceof ServerLevel level)||!job.canBeDoneBy(owner.citizenData())
                ||CitizenDecisionPolicy.isCriticalPreemption(owner.citizenData())
                ||!owner.citizenData().canTravelTo(job.target())||!isJobStillValid(level,job)||!isJobActionable(level,job))return false;
        if (currentJob != null && currentJob.id() == job.id()) {
            if (CitizenJobBoard.forLevel(level).touch(job.id(), owner.getUUID(), level.getGameTime())) return true;
            interrupt(true);
            return false;
        }
        if(isConstructionJob(level,job)){
            var board=CitizenJobBoard.forLevel(level);
            if(!board.reserve(job.id(),owner.getUUID(),level.getGameTime()))return false;
            owner.sleepController().cancel(true);owner.foodController().cancel(true);interrupt(false);
            owner.commandController().stop();clearBlockReason();currentJob=job;phase=WorkPhase.TRAVELLING;workProgress=0;return true;
        }
        if (isLadderBuildJob(level, job)) {
            var board = CitizenJobBoard.forLevel(level);
            if (!board.reserve(job.id(), owner.getUUID(), level.getGameTime())) return false;
            owner.sleepController().cancel(true);
            owner.foodController().cancel(true);
            interrupt(true);
            if (beginLadderBuild(level, job)) return true;
            board.release(job.id(), owner.getUUID());
            return false;
        }
        var approach=findJobApproachPosition(level,job).orElse(null);
        if(approach==null){blocked(WorkBlockReason.NO_PATH);return false;}
        var board=CitizenJobBoard.forLevel(level);
        if(!board.reserve(job.id(),owner.getUUID(),level.getGameTime()))return false;
        owner.sleepController().cancel(true);
        owner.foodController().cancel(true);
        interrupt(false);
        if(!owner.commandController().issueSystemMove(approach,CitizenBrainState.WORK)){board.release(job.id(),owner.getUUID());blocked(WorkBlockReason.NO_PATH);return false;}
        clearBlockReason();currentJob=job;phase=WorkPhase.TRAVELLING;workProgress=0;return true;
    }

    private boolean beginLadderBuild(ServerLevel level, CitizenJob job) {
        clearBlockReason();
        CitizenInventory inventory = owner.citizenData().inventory();
        currentJob = job;
        phase = WorkPhase.TRAVELLING;
        workProgress = 0.0D;

        if (inventory.countPersonalItem(Items.LADDER) > 0) {
            BlockPos approach = findLadderSiteApproach(level, job.target()).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                clearLocalState();
                return false;
            }
            ladderBuildPhase = LadderBuildPhase.TO_SITE;
            owner.setBrainState(CitizenBrainState.WORK);
            return true;
        }

        DeliveryRoute supply = findLadderSupply(level).orElse(null);
        if (supply == null) {
            clearLocalState();
            return false;
        }

        if (!owner.commandController().issueSystemMove(supply.approach(), CitizenBrainState.WORK)) {
            clearLocalState();
            blocked(WorkBlockReason.NO_PATH);
            return false;
        }
        ladderStorageTarget = supply.storage().immutable();
        ladderBuildPhase = LadderBuildPhase.TO_STORAGE;
        owner.setBrainState(CitizenBrainState.WORK);
        return true;
    }

    private Optional<DeliveryRoute> findLadderSupply(ServerLevel level) {
        var candidates = StorageData.forLevel(level).containersWithItem(level, owner.blockPosition(),
                stack -> stack.is(Items.LADDER), STORAGE_SEARCH_RADIUS).stream()
                .filter(target -> CitizenStorageAccess.mayUse(owner, target)
                        && owner.citizenData().canTravelTo(target)).toList();
        var route = dev.stonebanner.storage.DeliveryPlanner.choose(candidates, 8, target ->
                findReachableStorageApproach(level, target).map(approach -> new DeliveryRoute(target, approach)));
        if (route.isEmpty()) blocked(candidates.isEmpty() ? WorkBlockReason.MATERIALS : WorkBlockReason.NO_PATH);
        return route;
    }

    private void tickLadderBuild(ServerLevel level) {
        ExcavationLadderTaskData taskData = ExcavationLadderTaskData.forLevel(level);
        ExcavationLadderTaskData.LadderTask task = taskData.task(currentJob.target()).orElse(null);
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        if (task == null) {
            board.remove(currentJob.id());
            clearLocalState();
            owner.setBrainState(CitizenBrainState.IDLE);
            return;
        }

        if (level.getBlockState(task.target()).is(Blocks.LADDER)) {
            taskData.complete(task.target());
            board.complete(currentJob.id(), owner.getUUID());
            clearLocalState();
            owner.setBrainState(CitizenBrainState.IDLE);
            return;
        }

        if (ladderBuildPhase == LadderBuildPhase.TO_STORAGE) {
            if (ladderStorageTarget == null) {
                deferCurrentJob(level);
                return;
            }
            if (!CitizenStorageAccess.mayUse(owner, ladderStorageTarget)) {
                blocked(WorkBlockReason.MATERIALS);
                deferCurrentJob(level);
                return;
            }
            if (!owner.citizenData().canTravelTo(ladderStorageTarget)) {
                blocked(WorkBlockReason.NO_PATH);
                deferCurrentJob(level);
                return;
            }
            if (!isWithinWorkRange(ladderStorageTarget) || !storageVisible(level, owner.getEyePosition(), ladderStorageTarget)) {
                if (!owner.commandController().hasActiveCommand()) {
                    BlockPos approach = findReachableStorageApproach(level, ladderStorageTarget).orElse(null);
                    if (approach == null
                            || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                        deferCurrentJob(level);
                    }
                }
                return;
            }

            owner.commandController().stop();
            StorageData storage = StorageData.forLevel(level);
            StorageData.Extraction extraction = storage.extractAt(
                    level,
                    ladderStorageTarget,
                    stack -> stack.is(Items.LADDER),
                    1
            );
            if (extraction.isEmpty()) {
                blocked(WorkBlockReason.MATERIALS);
                deferCurrentJob(level);
                return;
            }

            ItemStack material = extraction.stacks().get(0);
            ItemStack remainder = owner.citizenData().inventory().add(material);
            if (!remainder.isEmpty()) {
                storage.insertAt(level, ladderStorageTarget, remainder);
                deferCurrentJob(level);
                return;
            }

            BlockPos approach = findLadderSiteApproach(level, task.target()).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                deferCurrentJob(level);
                return;
            }
            ladderStorageTarget = null;
            ladderBuildPhase = LadderBuildPhase.TO_SITE;
            return;
        }

        if (ladderBuildPhase == LadderBuildPhase.TO_SITE) {
            if (!isWithinWorkRange(task.target())) {
                if (!owner.commandController().hasActiveCommand()) {
                    BlockPos approach = findLadderSiteApproach(level, task.target()).orElse(null);
                    if (approach == null
                            || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                        deferCurrentJob(level);
                    }
                }
                return;
            }
            owner.commandController().stop();
            ladderBuildPhase = LadderBuildPhase.PLACING;
        }

        if (ladderBuildPhase == LadderBuildPhase.PLACING) {
            CitizenInventory inventory = owner.citizenData().inventory();
            if (inventory.countPersonalItem(Items.LADDER) <= 0) {
                deferCurrentJob(level);
                return;
            }
            BlockState placement = ExcavationLadderAutomation.placementState(level, task).orElse(null);
            if (placement == null) {
                deferCurrentJob(level);
                return;
            }
            if (!level.setBlock(task.target(), placement, 3)) {
                deferCurrentJob(level);
                return;
            }
            inventory.removePersonalItem(Items.LADDER, 1);
            owner.citizenData().practice(CitizenSkill.CONSTRUCTION,5);
            taskData.complete(task.target());
            ExcavationPlanData plans = ExcavationPlanData.forLevel(level);
            plans.markWorldChanged(task.target());
            plans.reconcileDirty(level);
            board.complete(currentJob.id(), owner.getUUID());
            clearLocalState();
            owner.setBrainState(CitizenBrainState.IDLE);
            acquireCooldown = 5;
        }
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
                interrupt(false); blocked(WorkBlockReason.NO_PATH);
            }
        }
    }

    private void tickWorking(ServerLevel level) {
        if (!isWithinWorkRange(currentJob.target())) {
            BlockPos approach = findJobApproachPosition(level, currentJob).orElse(null);
            if (approach == null || !owner.commandController().issueSystemMove(approach, CitizenBrainState.WORK)) {
                interrupt(false); blocked(WorkBlockReason.NO_PATH);
                return;
            }
            phase = WorkPhase.TRAVELLING;
            return;
        }

        if (isDestructiveWork() && (workProgress == 0 || blockReason != WorkBlockReason.NONE)
                && !destructionPermitted(level)) return;
        clearBlockReason();
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

        if (isDestructiveWork() && !destructionPermitted(level)) return;
        completeCurrentJob(level);
    }

    /** Checked before solid-block work starts, after a pause, and immediately before destruction. */
    private boolean isDestructiveWork() {
        return currentJob.workType() == WorkType.MINING || currentJob.workType() == WorkType.FORESTRY
                || currentJob.workType() == WorkType.CLEARING && !owner.level().getBlockState(currentJob.target())
                    .getCollisionShape(owner.level(), currentJob.target()).isEmpty();
    }

    private boolean destructionPermitted(ServerLevel level) {
        if (isOccupiedByOtherLivingEntity(level, currentJob.target())) {
            blocked(WorkBlockReason.OCCUPIED); return false;
        }
        ExcavationPlanData plans = ExcavationPlanData.forLevel(level);
        if (plans.containsActiveTarget(currentJob.target()) && !ExcavationOreDiscovery.mayMine(level, currentJob.target())) {
            blocked(WorkBlockReason.ORE_PERMISSION); deferUnsafeExcavation(level); return false;
        }
        if (!ExcavationEgressSafety.canSafelyMine(level, plans, owner.blockPosition(), currentJob.target())) {
            blocked(WorkBlockReason.UNSAFE_EXIT); deferUnsafeExcavation(level); return false;
        }
        clearBlockReason(); return true;
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
            deliveryStatus = dev.stonebanner.storage.DeliveryStatus.PAUSED_NEEDS;
            cargoStorageTarget = null;
            return;
        }

        // Manual Move/Follow/Attack commands take precedence over an idle/waiting delivery.
        if (cargoPhase != CargoPhase.TRAVELLING && owner.commandController().hasActiveCommand()) {
            deliveryStatus = dev.stonebanner.storage.DeliveryStatus.PLAYER_COMMAND;
            return;
        }
        // Membership can change while walking or between arrival and the deposit tick.
        // Keep the physical cargo and reselect; never blacklist an otherwise valid store.
        if (cargoStorageTarget != null && (!CitizenStorageAccess.mayUse(owner, cargoStorageTarget)
                || inventory.haulCargoSnapshot().stream().noneMatch(c -> CargoOwnership.mayDeliver(owner, c.owner(), cargoStorageTarget))
                || !owner.citizenData().canTravelTo(cargoStorageTarget))) {
            owner.commandController().stop();
            cargoStorageTarget = null;
            cargoPhase = CargoPhase.WAITING_STORAGE;
            deliveryStatus = dev.stonebanner.storage.DeliveryStatus.WAITING_STORAGE;
            cargoRetryCooldown = 0;
        }
        if (cargoRetryCooldown > 0) {
            cargoRetryCooldown--;
            return;
        }

        if (cargoStorageTarget == null) {
            StorageData storage = StorageData.forLevel(level);
            var offered = inventory.haulCargoSnapshot().stream().map(CitizenInventory.HaulCargo::stack).toList();
            var candidates = storage.acceptingContainers(level, owner.blockPosition(), offered, STORAGE_SEARCH_RADIUS)
                    .stream().filter(target -> inventory.haulCargoSnapshot().stream().anyMatch(c ->
                            CargoOwnership.mayDeliver(owner, c.owner(), target)
                                    && storage.canAcceptAt(level, target, c.stack()))).toList();
            var route = dev.stonebanner.storage.DeliveryPlanner.choose(candidates, 8, target -> {
                if (!owner.citizenData().canTravelTo(target)
                        || level.getGameTime() < failedCargoStorages.getOrDefault(target, Long.MIN_VALUE)) return Optional.<DeliveryRoute>empty();
                return findReachableStorageApproach(level, target).map(approach -> new DeliveryRoute(target, approach));
            });
            if (route.isEmpty()) {
                cargoPhase = CargoPhase.WAITING_STORAGE;
                deliveryStatus = candidates.isEmpty() ? dev.stonebanner.storage.DeliveryStatus.WAITING_STORAGE
                        : dev.stonebanner.storage.DeliveryStatus.BLOCKED_ROUTE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
                owner.setBrainState(CitizenBrainState.IDLE);
                return;
            }
            DeliveryRoute selected = route.get();
            if (!owner.commandController().issueSystemMove(selected.approach(), CitizenBrainState.WORK)) {
                rememberFailedStorage(level, selected.storage());
                cargoPhase = CargoPhase.WAITING_STORAGE;
                deliveryStatus = dev.stonebanner.storage.DeliveryStatus.BLOCKED_ROUTE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
                return;
            }
            cargoStorageTarget = selected.storage();
            cargoPhase = CargoPhase.TRAVELLING;
            deliveryStatus = dev.stonebanner.storage.DeliveryStatus.TRAVELLING;
            owner.setBrainState(CitizenBrainState.WORK);
            return;
        }

        if (cargoPhase == CargoPhase.TRAVELLING) {
            if (isWithinWorkRange(cargoStorageTarget) && storageVisible(level, owner.getEyePosition(), cargoStorageTarget)) {
                owner.commandController().stop();
                cargoPhase = CargoPhase.DEPOSITING;
                deliveryStatus = dev.stonebanner.storage.DeliveryStatus.DEPOSITING;
            } else if (!owner.commandController().hasActiveCommand()) {
                rememberFailedStorage(level, cargoStorageTarget);
                deliveryStatus = dev.stonebanner.storage.DeliveryStatus.BLOCKED_ROUTE;
                cargoStorageTarget = null;
                cargoPhase = CargoPhase.WAITING_STORAGE;
                cargoRetryCooldown = CARGO_RETRY_TICKS;
            }
            return;
        }

        if (cargoPhase == CargoPhase.DEPOSITING) {
            if (!isWithinWorkRange(cargoStorageTarget) || !storageVisible(level, owner.getEyePosition(), cargoStorageTarget)) {
                rememberFailedStorage(level, cargoStorageTarget);
                cargoStorageTarget = null; cargoPhase = CargoPhase.WAITING_STORAGE;
                deliveryStatus = dev.stonebanner.storage.DeliveryStatus.BLOCKED_ROUTE;
                cargoRetryCooldown = CARGO_RETRY_TICKS; return;
            }
            depositCargo(level, cargoStorageTarget);
            if (!inventory.hasHaulCargo()) {
                resetCargoDelivery();
                owner.setBrainState(CitizenBrainState.IDLE);
                acquireCooldown = 5;
                return;
            }
            cargoStorageTarget = null;
            cargoPhase = CargoPhase.WAITING_STORAGE;
            deliveryStatus = dev.stonebanner.storage.DeliveryStatus.WAITING_STORAGE;
            cargoRetryCooldown = CARGO_RETRY_TICKS;
        }
    }

    private void rememberFailedStorage(ServerLevel level, BlockPos storage) {
        failedCargoStorages.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        failedCargoStorages.put(storage.immutable(), level.getGameTime() + CARGO_RETRY_TICKS * 3L);
    }

    private boolean storageVisible(ServerLevel level, Vec3 eyes, BlockPos target) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eyes, Vec3.atCenterOf(target),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    /** Try adjacent standing points, including an alternate side of a blocked container. */
    private Optional<BlockPos> findReachableStorageApproach(ServerLevel level, BlockPos target) {
        if (storageRouteTick != level.getGameTime()) {
            storageRouteTick = level.getGameTime(); storageRouteBudget = 24; storageApproachCache.clear();
        }
        Optional<BlockPos> cached = storageApproachCache.get(target);
        if (cached != null) return cached;
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) for (int dy : new int[]{0, -1, 1}) {
            BlockPos pos = target.relative(direction).offset(0, dy, 0);
            if (BlockPathfinder.isWalkable(level, pos)
                    && storageVisible(level, BlockPathfinder.waypoint(level, pos).add(0, owner.getEyeHeight(), 0), target)) candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(pos -> owner.distanceToSqr(Vec3.atCenterOf(pos))));
        Optional<BlockPos> result = dev.stonebanner.storage.DeliveryPlanner.choose(candidates, 3, pos -> {
            if (storageRouteBudget <= 0) return Optional.empty();
            storageRouteBudget--;
            return BlockPathfinder.findPath(level, owner.blockPosition(), pos).isPresent() ? Optional.of(pos.immutable()) : Optional.empty();
        });
        storageApproachCache.put(target.immutable(), result);
        return result;
    }

    private void depositCargo(ServerLevel level, BlockPos storageTarget) {
        if (!CitizenStorageAccess.mayUse(owner, storageTarget)) return;
        StorageData storage = StorageData.forLevel(level);
        CitizenInventory inventory = owner.citizenData().inventory();
        for (CitizenInventory.HaulCargo cargo : inventory.haulCargoSnapshot()) {
            if (!CargoOwnership.mayDeliver(owner, cargo.owner(), storageTarget)) continue;
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

    private void deferCurrentJob(ServerLevel level) {
        if (currentJob != null) {
            CitizenJobBoard.forLevel(level).release(currentJob.id(), owner.getUUID());
        }
        owner.commandController().stop();
        clearLocalState();
        owner.setBrainState(CitizenBrainState.IDLE);
        acquireCooldown = ACQUIRE_INTERVAL_TICKS;
    }

    private void completeCurrentJob(ServerLevel level) {
        CitizenJob job = currentJob;
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        ExcavationPlanData excavationPlans = ExcavationPlanData.forLevel(level);
        Set<UUID> itemEntitiesBefore = nearbyItemEntityIds(level, job.target());

        boolean completed = switch (job.workType()) {
            case FORESTRY, MINING, CLEARING -> {
                boolean changed = level.destroyBlock(job.target(), true, owner);
                if (changed) dev.stonebanner.geology.ChunkResourceCache.invalidate(level, job.target());
                yield changed;
            }
            default -> false;
        };

        if (completed) {
            owner.citizenData().practice(CitizenSkillRules.skillFor(job.workType()),5);
            if (owner.citizenData().workPriority(WorkType.HAULING) != WorkPriority.DISABLED) {
                collectNewWorkDrops(level, job.target(), itemEntitiesBefore);
            }
            DroppedItemHauling.publishIfNeeded(level, job.target());
            if (job.workType() == WorkType.MINING) {
                ExcavationLadderAutomation.onMiningCompleted(level, excavationPlans, job.target());
                ExcavationOreDiscovery.scanNewlyExposed(level, excavationPlans, job.target());
            }
            excavationPlans.markWorldChanged(job.target());
            excavationPlans.reconcileDirty(level);
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
        int pickedUp = DroppedItemHauling.collectInto(level, job.target(), owner);
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
            UUID workOwner = CargoOwnership.workOwner(owner);
            CargoOwnership.markDrop(itemEntity, workOwner);
            ItemStack remainder = inventory.addHaulCargo(original, workOwner);
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
        return currentJob != null || cargoPhase == CargoPhase.TRAVELLING || cargoPhase == CargoPhase.DEPOSITING;
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

    private Optional<BlockPos> findLadderSiteApproach(ServerLevel level, BlockPos target) {
        BlockPos existingLadderAbove = target.above();
        if (BlockPathfinder.isWalkable(level, existingLadderAbove)) {
            return Optional.of(existingLadderAbove.immutable());
        }
        return findApproachPosition(level, target);
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

    private boolean isOccupiedByOtherLivingEntity(ServerLevel level, BlockPos target) {
        AABB safetyVolume = WorkSiteSafety.occupiedVolume(target, level.getBlockState(target).getCollisionShape(level, target));
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, safetyVolume,
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private boolean isJobActionable(ServerLevel level, CitizenJob job) {
        if(isConstructionJob(level,job))return dev.stonebanner.construction.ConstructionService.allowed(owner,job);
        if (isProductionJob(job)) return dev.stonebanner.production.ProductionService.allowed(owner,job);
        if (job.workType() == WorkType.HAULING) {
            var drops = DroppedItemHauling.stacksAt(level, job.target(), owner);
            if (drops.isEmpty()) return false;
            var candidates = StorageData.forLevel(level).acceptingContainers(level, job.target(), drops, STORAGE_SEARCH_RADIUS)
                    .stream().filter(target -> CitizenStorageAccess.mayUse(owner, target)).toList();
            return dev.stonebanner.storage.DeliveryPlanner.choose(candidates, 8, target ->
                    owner.citizenData().canTravelTo(target) ? findReachableStorageApproach(level, target) : Optional.<BlockPos>empty()).isPresent();
        }

        if (isLadderBuildJob(level, job)) {
            ExcavationLadderTaskData.LadderTask task = ExcavationLadderTaskData.forLevel(level)
                    .task(job.target())
                    .orElse(null);
            if (task == null || ExcavationLadderAutomation.placementState(level, task).isEmpty()) {
                return false;
            }
            if (owner.citizenData().inventory().countPersonalItem(Items.LADDER) > 0) {
                return findLadderSiteApproach(level, task.target()).isPresent();
            }
            return findLadderSupply(level).isPresent()
                    && findLadderSiteApproach(level, task.target()).isPresent();
        }
        return true;
    }

    private boolean isJobStillValid(ServerLevel level, CitizenJob job) {
        if(isConstructionJob(level,job))return dev.stonebanner.construction.ConstructionService.valid(level,job);
        if (isProductionJob(job)) return dev.stonebanner.production.ProductionService.valid(level,job);
        if (job.workType() == WorkType.HAULING) {
            return DroppedItemHauling.hasDroppedItems(level, job.target());
        }
        if (job.workType() == WorkType.BUILDING) {
            ExcavationLadderTaskData taskData = ExcavationLadderTaskData.forLevel(level);
            if (taskData.task(job.target()).isEmpty()) {
                return false;
            }
            if (level.getBlockState(job.target()).is(Blocks.LADDER)) {
                taskData.complete(job.target());
                return false;
            }
            return true;
        }
        return WorkTargetRules.isValid(job.workType(), level, job.target());
    }

    private boolean isConstructionJob(ServerLevel level,CitizenJob job){
        return job!=null&&job.workType()==WorkType.BUILDING&&dev.stonebanner.construction.ConstructionData.forLevel(level).at(job.target())!=null;
    }
    private boolean isLadderBuildJob(ServerLevel level, CitizenJob job) {
        return job != null
                && job.workType() == WorkType.BUILDING
                && ExcavationLadderTaskData.forLevel(level).task(job.target()).isPresent();
    }

    private static boolean isProductionJob(CitizenJob job){return job!=null&&(job.workType()==WorkType.FARMING||job.workType()==WorkType.CRAFTING);}

    private double workRate(CitizenJob job) {
        return CitizenSkillRules.workRate(owner.citizenData(),job.workType());
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
        production.clear();construction.clear();
        currentJob = null;
        phase = WorkPhase.IDLE;
        workProgress = 0.0D;
        ladderBuildPhase = LadderBuildPhase.IDLE;
        ladderStorageTarget = null;
    }

    private void resetCargoDelivery() {
        cargoPhase = CargoPhase.IDLE;
        deliveryStatus = dev.stonebanner.storage.DeliveryStatus.IDLE;
        failedCargoStorages.clear();
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

    private enum LadderBuildPhase {
        IDLE,
        TO_STORAGE,
        TO_SITE,
        PLACING
    }
}
