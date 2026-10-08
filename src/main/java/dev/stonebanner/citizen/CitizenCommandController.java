package dev.stonebanner.citizen;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.navigation.LadderMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-side executor for high-level Human NPC commands. */
public final class CitizenCommandController {
    private static final double WAYPOINT_ARRIVAL_DISTANCE_SQR = 0.55D * 0.55D;
    private static final int MOVE_REFRESH_TICKS = 10;
    private static final int FOLLOW_REPATH_TICKS = 20;
    private static final int MAX_MOVE_REPLAN_ATTEMPTS = 3;
    private static final int MOVE_REPLAN_BACKOFF_TICKS = 8;
    private static final double MOVE_SPEED = 1.0D;

    private final HumanNpcEntity owner;
    private final Deque<BlockPos> path = new ArrayDeque<>();
    private final dev.stonebanner.command.MoveOrderQueue queuedMoves = new dev.stonebanner.command.MoveOrderQueue();

    private ActorCommand activeCommand;
    private UUID targetIdentity;
    private boolean defensiveAttack;
    private boolean unrestrictedRoute;
    private CitizenBrainState movementState = CitizenBrainState.IDLE;
    private CommandStatus status = CommandStatus.IDLE;
    private BlockPos moveDestination;
    private BlockPos attemptedDoor;
    private int navigationRefreshCooldown;
    private int attackCooldown;
    private int followRepathCooldown;
    private int moveReplanAttempts;
    private int moveReplanCooldown;

    public CitizenCommandController(HumanNpcEntity owner) {
        this.owner = owner;
    }

    public boolean issue(ActorCommand command) {
        if (command == null || owner.level().isClientSide || !owner.isAlive()) {
            return false;
        }

        if (command instanceof ActorCommand.Stop) {
            stop();
            return true;
        }
        if (!owner.citizenData().health().canMoveIndependently()) return false;
        if (command instanceof ActorCommand.MoveTo moveTo) {
            if (!owner.citizenData().canTravelTo(moveTo.target())) {
                return false;
            }
            queuedMoves.clear();
            unrestrictedRoute = false;
            return issueMove(moveTo.target(), CitizenBrainState.MOVE);
        }
        if(command instanceof ActorCommand.EntityAction action && action.action()==ActorCommand.EntityActionType.ATTACK) {
            Entity target=owner.level().getEntity(action.entityId());
            if(!(target instanceof net.minecraft.world.entity.monster.Monster)||!isUsableTarget(target)||!owner.citizenData().canTravelTo(target.blockPosition()))return false;
            stop(); activeCommand=command; targetIdentity=target.getUUID(); movementState=CitizenBrainState.FOLLOW; status=CommandStatus.FOLLOWING; return true;
        }
        if (command instanceof ActorCommand.FollowEntity follow) {
            Entity target = owner.level().getEntity(follow.entityId());
            if (!isUsableTarget(target) || !owner.citizenData().canTravelTo(target.blockPosition())) {
                stop();
                return false;
            }
            activeCommand = command;
            unrestrictedRoute = false;
            targetIdentity = target.getUUID();
            defensiveAttack = false;
            queuedMoves.clear();
            moveDestination = null;
            movementState = CitizenBrainState.FOLLOW;
            path.clear();
            navigationRefreshCooldown = 0;
            followRepathCooldown = 0;
            resetMoveRecovery();
            status = CommandStatus.FOLLOWING;
            owner.setBrainState(CitizenBrainState.FOLLOW);
            return true;
        }

        return false;
    }

    /** Internal Citizen AI movement, e.g. flee/return-home, deliberately bypasses player travel limits. */
    public boolean issueSystemMove(BlockPos target, CitizenBrainState state) {
        if (!owner.isAlive() || !owner.citizenData().health().canMoveIndependently()) return false;
        queuedMoves.clear();
        unrestrictedRoute = true;
        CitizenBrainState resolvedState = state == null ? CitizenBrainState.MOVE : state;
        return issueMove(target, resolvedState);
    }

    private boolean issueMove(BlockPos target, CitizenBrainState state) {
        targetIdentity = null;
        defensiveAttack = false;
        BlockPos immutableTarget = target.immutable();
        activeCommand = new ActorCommand.MoveTo(immutableTarget);
        moveDestination = immutableTarget;
        movementState = state;
        followRepathCooldown = 0;
        resetMoveRecovery();
        if (!rebuildPath(immutableTarget, state, CommandStatus.MOVING)) {
            failMove();
            return false;
        }
        return true;
    }

    public void tick() {
        if (owner.level().isClientSide) {
            return;
        }
        if (!owner.isAlive()) { stop(); return; }
        if (activeCommand == null) {
            BlockPos next = queuedMoves.poll();
            if (next == null) return;
            if (!owner.level().hasChunkAt(next) || !owner.citizenData().canTravelTo(next)) {
                failMove();
                return;
            }
            if (!issueMove(next, CitizenBrainState.MOVE) || activeCommand == null) return;
        }

        if(activeCommand instanceof ActorCommand.EntityAction action) {
            var target=owner.level().getEntity(action.entityId());
            if(!isCurrentTarget(target)||!owner.citizenData().canTravelTo(target.blockPosition())){stop();return;}
            if(attackCooldown>0)attackCooldown--;
            owner.getLookControl().setLookAt(target,30,30);
            if(owner.distanceToSqr(target)<4 && owner.getSensing().hasLineOfSight(target)) {
                owner.getNavigation().stop(); path.clear();
                if(attackCooldown==0){owner.doHurtTarget(target);owner.swing(InteractionHand.MAIN_HAND);attackCooldown=20;}
            } else {
                tickFollow(new ActorCommand.FollowEntity(action.entityId(),1));
            }
            if(activeCommand!=null)activeCommand=action;
            if (activeCommand != null && defensiveAttack) {
                movementState = CitizenBrainState.DEFEND;
                owner.setBrainState(CitizenBrainState.DEFEND);
            }
            return;
        }
        if (activeCommand instanceof ActorCommand.MoveTo) {
            tickPath(movementState, CommandStatus.MOVING);
            return;
        }
        if (activeCommand instanceof ActorCommand.FollowEntity follow) {
            tickFollow(follow);
        }
    }

    private void tickFollow(ActorCommand.FollowEntity follow) {
        Entity target = owner.level().getEntity(follow.entityId());
        if (!isCurrentTarget(target) || !owner.citizenData().canTravelTo(target.blockPosition())) {
            stop();
            return;
        }

        double preferredDistanceSqr = follow.preferredDistance() * follow.preferredDistance();
        if (owner.distanceToSqr(target) <= preferredDistanceSqr) {
            path.clear();
            owner.getNavigation().stop();
            status = CommandStatus.FOLLOWING;
            movementState = CitizenBrainState.FOLLOW;
            owner.setBrainState(CitizenBrainState.FOLLOW);
            navigationRefreshCooldown = 0;
            followRepathCooldown = Math.max(0, followRepathCooldown - 1);
            return;
        }

        if (followRepathCooldown <= 0) {
            if (!rebuildPath(BlockPos.containing(target.position()), CitizenBrainState.FOLLOW, CommandStatus.FOLLOWING)) {
                status = CommandStatus.UNREACHABLE;
                movementState = CitizenBrainState.FOLLOW;
                owner.setBrainState(CitizenBrainState.FOLLOW);
                activeCommand = follow;
                followRepathCooldown = FOLLOW_REPATH_TICKS;
                return;
            }
            activeCommand = follow;
            movementState = CitizenBrainState.FOLLOW;
            followRepathCooldown = FOLLOW_REPATH_TICKS;
        } else {
            followRepathCooldown--;
        }

        // Preserve UNREACHABLE and the retry delay instead of retrying every empty-path tick.
        if (path.isEmpty()) return;

        if (tickPath(CitizenBrainState.FOLLOW, CommandStatus.FOLLOWING)) {
            activeCommand = follow;
        }
    }

    private boolean tickPath(CitizenBrainState state, CommandStatus movingStatus) {
        if (!unrestrictedRoute && moveDestination != null
                && !owner.citizenData().canTravelTo(moveDestination)) {
            failMove();
            return false;
        }
        BlockPos next = path.peekFirst();
        if (next == null) {
            if (state != CitizenBrainState.FOLLOW && moveDestination != null && status == CommandStatus.REPATHING)
                return recoverMove(state, movingStatus);
            if (state == CitizenBrainState.FOLLOW) {
                status = CommandStatus.FOLLOWING;
                return true;
            }
            completeMove();
            return true;
        }

        Vec3 waypoint = BlockPathfinder.waypoint(owner.level(), next);
        if (owner.distanceToSqr(waypoint.x, waypoint.y, waypoint.z) <= WAYPOINT_ARRIVAL_DISTANCE_SQR) {
            path.removeFirst();
            moveReplanAttempts = 0;
            moveReplanCooldown = 0;
            attemptedDoor = null;
            next = path.peekFirst();
            if (next == null) {
                if (state == CitizenBrainState.FOLLOW) {
                    owner.getNavigation().stop();
                    status = CommandStatus.FOLLOWING;
                    return true;
                }
                completeMove();
                return true;
            }
            waypoint = BlockPathfinder.waypoint(owner.level(), next);
        }

        if (BlockPathfinder.isClosedIronDoor(owner.level(), next)) {
            if (!handleControlledIronDoor(next, state, movingStatus)) {
                return false;
            }
            if (BlockPathfinder.isClosedIronDoor(owner.level(), next)) {
                owner.getNavigation().stop();
                status = movingStatus;
                movementState = state;
                owner.setBrainState(state);
                return true;
            }
        } else {
            attemptedDoor = null;
        }

        if ((!unrestrictedRoute && !owner.citizenData().canTravelTo(next))
                || !BlockPathfinder.isWalkable(owner.level(), next)) {
            return recoverMove(state, movingStatus);
        }

        Optional<Direction> ladderSupport = climbDirection(next);
        if (ladderSupport.isPresent()) {
            owner.getNavigation().stop();
            navigationRefreshCooldown = 0;
            Vec3 velocity = LadderMovement.citizenVelocity(
                    owner.position(),
                    waypoint,
                    ladderSupport.get(),
                    owner.citizenData().health().movementMultiplier()
            );
            owner.setDeltaMovement(velocity);
            owner.getLookControl().setLookAt(waypoint.x, waypoint.y + owner.getEyeHeight(), waypoint.z);
            status = movingStatus;
            movementState = state;
            owner.setBrainState(state);
            return true;
        }

        if (navigationRefreshCooldown <= 0 || owner.getNavigation().isDone()) {
            double injuryAdjustedSpeed = MOVE_SPEED * owner.citizenData().health().movementMultiplier();
            // The custom route is made of adjacent blocks. Vanilla's coordinate overload uses accuracy 1,
            // which treats the next adjacent block as already reached and leaves the mob standing still.
            Path segment = owner.getNavigation().createPath(next, 0);
            if (segment == null || !segment.canReach()
                    || !owner.getNavigation().moveTo(segment, injuryAdjustedSpeed)) {
                return recoverMove(state, movingStatus);
            }
            navigationRefreshCooldown = MOVE_REFRESH_TICKS;
        } else {
            navigationRefreshCooldown--;
        }

        status = movingStatus;
        movementState = state;
        owner.setBrainState(state);
        return true;
    }

    private Optional<Direction> climbDirection(BlockPos next) {
        BlockPos current = owner.blockPosition();
        return BlockPathfinder.climbDirection(owner.level(), next)
                .or(() -> BlockPathfinder.climbDirection(owner.level(), current))
                .or(() -> BlockPathfinder.climbDirection(owner.level(), current.below()))
                .or(() -> BlockPathfinder.climbDirection(owner.level(), next.below()));
    }

    private boolean handleControlledIronDoor(BlockPos doorPos, CitizenBrainState state, CommandStatus movingStatus) {
        Optional<BlockPos> control = BlockPathfinder.findNearbyDoorControl(owner.level(), doorPos);
        if (control.isEmpty()) {
            return recoverMove(state, movingStatus);
        }

        if (doorPos.equals(attemptedDoor)) {
            return recoverMove(state, movingStatus);
        }

        attemptedDoor = doorPos.immutable();
        if (!activateDoorControl(control.get())) {
            return recoverMove(state, movingStatus);
        }
        navigationRefreshCooldown = 0;
        return true;
    }

    private boolean activateDoorControl(BlockPos controlPos) {
        if (!(owner.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState controlState = serverLevel.getBlockState(controlPos);
        if (!BlockPathfinder.isDoorControl(controlState)) {
            return false;
        }

        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(serverLevel);
        fakePlayer.setPos(owner.getX(), owner.getY(), owner.getZ());
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(controlPos),
                Direction.UP,
                controlPos,
                false
        );
        InteractionResult result = controlState.use(serverLevel, fakePlayer, InteractionHand.MAIN_HAND, hit);
        return result.consumesAction();
    }

    private boolean recoverMove(CitizenBrainState state, CommandStatus movingStatus) {
        owner.getNavigation().stop();
        navigationRefreshCooldown = 0;

        if (state == CitizenBrainState.FOLLOW) {
            path.clear();
            status = CommandStatus.UNREACHABLE;
            return false;
        }
        if (moveDestination == null) {
            failMove();
            return false;
        }
        if (moveReplanCooldown > 0) {
            moveReplanCooldown--;
            status = CommandStatus.REPATHING;
            movementState = state;
            owner.setBrainState(state);
            return true;
        }
        if (moveReplanAttempts >= MAX_MOVE_REPLAN_ATTEMPTS) {
            failMove();
            return false;
        }

        moveReplanAttempts++;
        Optional<List<BlockPos>> result = routeTo(moveDestination);
        path.clear();
        if (result.isEmpty()) {
            moveReplanCooldown = MOVE_REPLAN_BACKOFF_TICKS;
            status = CommandStatus.REPATHING;
            movementState = state;
            owner.setBrainState(state);
            return true;
        }

        path.addAll(result.get());
        attemptedDoor = null;
        moveReplanCooldown = 0;
        status = path.isEmpty() ? CommandStatus.IDLE : movingStatus;
        movementState = path.isEmpty() ? CitizenBrainState.IDLE : state;
        owner.setBrainState(movementState);
        if (path.isEmpty()) {
            completeMove();
        }
        return true;
    }

    private boolean rebuildPath(BlockPos target, CitizenBrainState state, CommandStatus movingStatus) {
        Optional<List<BlockPos>> result = routeTo(target);

        path.clear();
        owner.getNavigation().stop();
        if (result.isEmpty()) {
            status = CommandStatus.UNREACHABLE;
            movementState = CitizenBrainState.IDLE;
            owner.setBrainState(CitizenBrainState.IDLE);
            return false;
        }

        path.addAll(result.get());
        navigationRefreshCooldown = 0;
        status = path.isEmpty() ? CommandStatus.IDLE : movingStatus;
        movementState = path.isEmpty() ? CitizenBrainState.IDLE : state;
        owner.setBrainState(movementState);
        if (path.isEmpty() && state != CitizenBrainState.FOLLOW) {
            activeCommand = null;
            moveDestination = null;
        }
        return true;
    }

    private Optional<List<BlockPos>> routeTo(BlockPos target) {
        return BlockPathfinder.findPath(owner.level(), owner.blockPosition(), target,
                pos -> owner.level().hasChunkAt(pos)
                        && (unrestrictedRoute || owner.citizenData().canTravelTo(pos)));
    }

    public void stop() {
        unrestrictedRoute = false;
        targetIdentity = null;
        defensiveAttack = false;
        attackCooldown = 0;
        queuedMoves.clear();
        activeCommand = null;
        moveDestination = null;
        path.clear();
        navigationRefreshCooldown = 0;
        followRepathCooldown = 0;
        resetMoveRecovery();
        movementState = CitizenBrainState.IDLE;
        owner.getNavigation().stop();
        status = CommandStatus.IDLE;
        owner.setBrainState(CitizenBrainState.IDLE);
    }

    private void completeMove() {
        unrestrictedRoute = false;
        activeCommand = null;
        moveDestination = null;
        path.clear();
        owner.getNavigation().stop();
        navigationRefreshCooldown = 0;
        followRepathCooldown = 0;
        resetMoveRecovery();
        movementState = CitizenBrainState.IDLE;
        status = CommandStatus.IDLE;
        owner.setBrainState(CitizenBrainState.IDLE);
    }

    private void failMove() {
        unrestrictedRoute = false;
        queuedMoves.clear();
        activeCommand = null;
        moveDestination = null;
        path.clear();
        owner.getNavigation().stop();
        navigationRefreshCooldown = 0;
        followRepathCooldown = 0;
        resetMoveRecovery();
        movementState = CitizenBrainState.IDLE;
        status = CommandStatus.UNREACHABLE;
        owner.setBrainState(CitizenBrainState.IDLE);
    }

    private void resetMoveRecovery() {
        attemptedDoor = null;
        moveReplanAttempts = 0;
        moveReplanCooldown = 0;
    }

    private boolean isUsableTarget(Entity target) {
        return target != null && target.isAlive() && target != owner;
    }

    private boolean isCurrentTarget(Entity target) {
        return isUsableTarget(target) && target.getUUID().equals(targetIdentity);
    }

    public void defendFrom(Entity target) {
        if (!(activeCommand instanceof ActorCommand.EntityAction) || !isCurrentTarget(target)) {
            if (!issue(new ActorCommand.EntityAction(target.getId(), ActorCommand.EntityActionType.ATTACK))) {
                stop();
                owner.setBrainState(CitizenBrainState.DEFEND);
                return;
            }
        }
        defensiveAttack = true;
        movementState = CitizenBrainState.DEFEND;
        owner.setBrainState(CitizenBrainState.DEFEND);
    }

    public boolean isDefensiveAttack() { return defensiveAttack; }

    public boolean hasActiveCommand() {
        return activeCommand != null || queuedMoves.size() > 0;
    }

    public int queuedMoveCount() { return queuedMoves.size(); }

    public boolean queueMove(BlockPos target) {
        if (owner.level().isClientSide || !owner.isAlive() || owner.citizenData().health().needsRecovery() || !owner.citizenData().health().canMoveIndependently()
                || !owner.level().hasChunkAt(target)
                || !owner.citizenData().canTravelTo(target)) return false;
        // Work, fleeing and indefinite follow/attack are not silently converted into a waypoint queue.
        if (activeCommand != null && (!(activeCommand instanceof ActorCommand.MoveTo)
                || movementState != CitizenBrainState.MOVE)) return false;
        if (activeCommand == null && queuedMoves.size() == 0) return owner.issueCommand(new ActorCommand.MoveTo(target));
        return queuedMoves.offer(target);
    }

    public CitizenBrainState movementState() {
        return movementState;
    }

    public CommandStatus status() {
        return status;
    }

    public List<BlockPos> pathSnapshot() {
        return List.copyOf(path);
    }

    public enum CommandStatus {
        IDLE,
        MOVING,
        FOLLOWING,
        REPATHING,
        UNREACHABLE
    }
}
