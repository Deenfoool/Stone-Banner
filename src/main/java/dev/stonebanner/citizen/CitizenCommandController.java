package dev.stonebanner.citizen;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Server-side executor for high-level Human NPC commands. */
public final class CitizenCommandController {
    private static final double WAYPOINT_ARRIVAL_DISTANCE_SQR = 0.55D * 0.55D;
    private static final int MOVE_REFRESH_TICKS = 10;
    private static final int FOLLOW_REPATH_TICKS = 20;
    private static final double MOVE_SPEED = 1.0D;

    private final HumanNpcEntity owner;
    private final Deque<BlockPos> path = new ArrayDeque<>();

    private ActorCommand activeCommand;
    private CitizenBrainState movementState = CitizenBrainState.IDLE;
    private CommandStatus status = CommandStatus.IDLE;
    private int navigationRefreshCooldown;
    private int followRepathCooldown;

    public CitizenCommandController(HumanNpcEntity owner) {
        this.owner = owner;
    }

    public boolean issue(ActorCommand command) {
        if (command == null || owner.level().isClientSide) {
            return false;
        }

        if (command instanceof ActorCommand.Stop) {
            stop();
            return true;
        }
        if (command instanceof ActorCommand.MoveTo moveTo) {
            return issueMove(moveTo.target(), CitizenBrainState.MOVE);
        }
        if (command instanceof ActorCommand.FollowEntity follow) {
            Entity target = owner.level().getEntity(follow.entityId());
            if (!isUsableTarget(target)) {
                stop();
                return false;
            }
            activeCommand = command;
            movementState = CitizenBrainState.FOLLOW;
            path.clear();
            navigationRefreshCooldown = 0;
            followRepathCooldown = 0;
            status = CommandStatus.FOLLOWING;
            owner.setBrainState(CitizenBrainState.FOLLOW);
            return true;
        }

        return false;
    }

    /** Internal Citizen AI movement, e.g. flee/return-home, without inventing a second navigation stack. */
    public boolean issueSystemMove(BlockPos target, CitizenBrainState state) {
        CitizenBrainState resolvedState = state == null ? CitizenBrainState.MOVE : state;
        return issueMove(target, resolvedState);
    }

    private boolean issueMove(BlockPos target, CitizenBrainState state) {
        activeCommand = new ActorCommand.MoveTo(target);
        movementState = state;
        followRepathCooldown = 0;
        return rebuildPath(target, state, CommandStatus.MOVING);
    }

    public void tick() {
        if (owner.level().isClientSide || activeCommand == null) {
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
        if (!isUsableTarget(target)) {
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

        if (followRepathCooldown <= 0 || path.isEmpty()) {
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

        tickPath(CitizenBrainState.FOLLOW, CommandStatus.FOLLOWING);
        activeCommand = follow;
    }

    private void tickPath(CitizenBrainState state, CommandStatus movingStatus) {
        BlockPos next = path.peekFirst();
        if (next == null) {
            if (state == CitizenBrainState.FOLLOW) {
                status = CommandStatus.FOLLOWING;
                return;
            }
            completeMove();
            return;
        }

        Vec3 waypoint = BlockPathfinder.waypoint(owner.level(), next);
        if (owner.distanceToSqr(waypoint.x, waypoint.y, waypoint.z) <= WAYPOINT_ARRIVAL_DISTANCE_SQR) {
            path.removeFirst();
            next = path.peekFirst();
            if (next == null) {
                if (state == CitizenBrainState.FOLLOW) {
                    owner.getNavigation().stop();
                    status = CommandStatus.FOLLOWING;
                    return;
                }
                completeMove();
                return;
            }
            waypoint = BlockPathfinder.waypoint(owner.level(), next);
        }

        if (navigationRefreshCooldown <= 0 || owner.getNavigation().isDone()) {
            owner.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, MOVE_SPEED);
            navigationRefreshCooldown = MOVE_REFRESH_TICKS;
        } else {
            navigationRefreshCooldown--;
        }

        status = movingStatus;
        movementState = state;
        owner.setBrainState(state);
    }

    private boolean rebuildPath(BlockPos target, CitizenBrainState state, CommandStatus movingStatus) {
        Optional<List<BlockPos>> result = BlockPathfinder.findPath(
                owner.level(),
                BlockPos.containing(owner.position()),
                target
        );

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
        }
        return true;
    }

    public void stop() {
        activeCommand = null;
        path.clear();
        navigationRefreshCooldown = 0;
        followRepathCooldown = 0;
        movementState = CitizenBrainState.IDLE;
        owner.getNavigation().stop();
        status = CommandStatus.IDLE;
        owner.setBrainState(CitizenBrainState.IDLE);
    }

    private void completeMove() {
        activeCommand = null;
        path.clear();
        owner.getNavigation().stop();
        navigationRefreshCooldown = 0;
        followRepathCooldown = 0;
        movementState = CitizenBrainState.IDLE;
        status = CommandStatus.IDLE;
        owner.setBrainState(CitizenBrainState.IDLE);
    }

    private boolean isUsableTarget(Entity target) {
        return target != null && target.isAlive() && target != owner;
    }

    public boolean hasActiveCommand() {
        return activeCommand != null;
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
        UNREACHABLE
    }
}
