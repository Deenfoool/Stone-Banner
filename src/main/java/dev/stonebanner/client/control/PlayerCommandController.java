package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CameraSpace;
import dev.stonebanner.control.ControlMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** One entry point for player intent. Mouse control currently supplies Move and Stop commands. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class PlayerCommandController {
    private static final double ARRIVAL_DISTANCE = 0.55D;
    private static final int STUCK_REPLAN_TICKS = 30;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final Deque<BlockPos> path = new ArrayDeque<>();
    private static BlockPos destination;
    private static CommandStatus status = CommandStatus.IDLE;
    private static Vec3 lastProgressPosition;
    private static int stuckTicks;
    private static int replanCooldown;
    private static Integer selectedEntityId;
    private static PendingAction pendingAction = PendingAction.NONE;

    private PlayerCommandController() {
    }

    public static void moveTo(BlockHitResult hit) {
        Direction face = hit.getDirection();
        BlockPos requestedTarget;
        if (face == Direction.UP) {
            requestedTarget = hit.getBlockPos().above();
        } else {
            requestedTarget = hit.getBlockPos().relative(face);
        }
        selectedEntityId = null;
        pendingAction = PendingAction.NONE;
        createPath(requestedTarget);
    }

    public static void contextAction(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || entity == minecraft.player) {
            return;
        }

        selectedEntityId = entity.getId();
        pendingAction = entity instanceof Monster ? PendingAction.ATTACK
                : entity instanceof AbstractVillager ? PendingAction.INTERACT
                : PendingAction.NONE;
        facePlayerToward(minecraft.player, entity);

        if (pendingAction == PendingAction.NONE) {
            clearPath();
            status = CommandStatus.TARGET_SELECTED;
            return;
        }
        if (!executePendingActionIfInRange(minecraft.player, entity)) {
            createPath(BlockPos.containing(entity.position()));
        }
    }

    public static void stop() {
        path.clear();
        destination = null;
        selectedEntityId = null;
        pendingAction = PendingAction.NONE;
        status = CommandStatus.IDLE;
        resetProgressTracking();
    }

    public static Optional<Vec3> moveTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(path.peekFirst()).map(node -> BlockPathfinder.waypoint(minecraft.level, node));
    }

    public static boolean hasMoveTarget() {
        return !path.isEmpty();
    }

    public static List<BlockPos> pathSnapshot() {
        return List.copyOf(path);
    }

    public static Optional<BlockPos> destination() {
        return Optional.ofNullable(destination);
    }

    public static CommandStatus status() {
        return status;
    }

    public static Optional<Entity> selectedEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (selectedEntityId == null || minecraft.level == null) {
            return Optional.empty();
        }
        Entity entity = minecraft.level.getEntity(selectedEntityId);
        if (entity == null || !entity.isAlive()) {
            selectedEntityId = null;
            pendingAction = PendingAction.NONE;
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }

        if (ClientConfig.controlMode() != ControlMode.TACTICAL) {
            return;
        }

        Input input = event.getInput();
        clearMovement(input);
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (level == null) {
            stop();
            return;
        }

        Entity actionTarget = selectedEntity().orElse(null);
        if (actionTarget != null && pendingAction != PendingAction.NONE) {
            facePlayerToward(player, actionTarget);
            if (executePendingActionIfInRange(player, actionTarget)) {
                clearMovement(input);
                return;
            }
            BlockPos movingTarget = BlockPos.containing(actionTarget.position());
            if (destination != null
                    && horizontalBlockDistance(destination, movingTarget) > 2
                    && player.tickCount % 10 == 0) {
                destination = movingTarget;
                replan(level, BlockPos.containing(player.position()));
            }
        }
        if (path.isEmpty()) {
            return;
        }

        updateProgressAndReplan(player, level);
        BlockPos nextNode = path.peekFirst();
        if (nextNode == null) {
            finishMovement();
            return;
        }
        openDoorAhead(player, level, nextNode);
        if (!BlockPathfinder.isWalkable(level, nextNode)) {
            replan(level, BlockPos.containing(player.position()));
            nextNode = path.peekFirst();
            if (nextNode == null) {
                return;
            }
        }

        Vec3 moveTarget = BlockPathfinder.waypoint(level, nextNode);
        double deltaX = moveTarget.x - player.getX();
        double deltaZ = moveTarget.z - player.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double waypointArrival = path.size() == 1 ? ARRIVAL_DISTANCE : 0.35D;
        if (distance <= waypointArrival) {
            path.removeFirst();
            if (path.isEmpty()) {
                finishMovement();
                return;
            }
            nextNode = path.peekFirst();
            moveTarget = BlockPathfinder.waypoint(level, nextNode);
            deltaX = moveTarget.x - player.getX();
            deltaZ = moveTarget.z - player.getZ();
            distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        }

        double worldX = deltaX / distance;
        double worldZ = deltaZ / distance;
        CameraSpace.MovementVector movement = CameraSpace.worldToLocal(worldX, worldZ, player.getYRot());

        input.leftImpulse = Mth.clamp(movement.left(), -1.0F, 1.0F);
        input.forwardImpulse = Mth.clamp(movement.forward(), -1.0F, 1.0F);
        input.left = input.leftImpulse > 0.01F;
        input.right = input.leftImpulse < -0.01F;
        input.up = input.forwardImpulse > 0.01F;
        input.down = input.forwardImpulse < -0.01F;
        if (player.isInWater()) {
            input.jumping = moveTarget.y > player.getY() + 0.25D;
            input.shiftKeyDown = moveTarget.y < player.getY() - 0.40D;
        } else {
            input.jumping = player.onGround()
                    && (player.horizontalCollision || moveTarget.y > player.getY() + 0.35D);
            input.shiftKeyDown = false;
        }
    }

    private static void createPath(BlockPos requestedTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        destination = requestedTarget.immutable();
        BlockPos start = BlockPos.containing(minecraft.player.position());
        Optional<List<BlockPos>> result = BlockPathfinder.findPath(minecraft.level, start, destination);
        path.clear();
        result.ifPresent(path::addAll);
        status = result.isPresent() ? (path.isEmpty() ? CommandStatus.IDLE : CommandStatus.MOVING)
                : CommandStatus.UNREACHABLE;
        if (result.isEmpty()) {
            destination = null;
        }
        resetProgressTracking();
    }

    private static void updateProgressAndReplan(Player player, ClientLevel level) {
        if (replanCooldown > 0) {
            replanCooldown--;
        }
        Vec3 currentPosition = player.position();
        if (lastProgressPosition == null || currentPosition.distanceToSqr(lastProgressPosition) > 0.01D) {
            lastProgressPosition = currentPosition;
            stuckTicks = 0;
            return;
        }

        stuckTicks++;
        if (stuckTicks >= STUCK_REPLAN_TICKS && replanCooldown == 0) {
            replan(level, BlockPos.containing(currentPosition));
        }
    }

    private static void replan(ClientLevel level, BlockPos start) {
        if (destination == null) {
            return;
        }
        Optional<List<BlockPos>> result = BlockPathfinder.findPath(level, start, destination);
        path.clear();
        result.ifPresent(path::addAll);
        status = result.isPresent() ? (path.isEmpty() ? CommandStatus.IDLE : CommandStatus.MOVING)
                : CommandStatus.UNREACHABLE;
        if (result.isPresent() && path.isEmpty()) {
            destination = null;
        }
        if (result.isEmpty()) {
            destination = null;
        }
        stuckTicks = 0;
        replanCooldown = REPLAN_COOLDOWN_TICKS;
    }

    private static void finishMovement() {
        clearPath();
        status = selectedEntityId == null ? CommandStatus.IDLE : CommandStatus.TARGET_SELECTED;
        resetProgressTracking();
    }

    private static boolean executePendingActionIfInRange(Player player, Entity target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || pendingAction == PendingAction.NONE
                || player.distanceToSqr(target) > 9.0D) {
            return false;
        }

        facePlayerToward(player, target);
        if (pendingAction == PendingAction.ATTACK) {
            minecraft.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        } else if (pendingAction == PendingAction.INTERACT) {
            InteractionResult result = minecraft.gameMode.interact(player, target, InteractionHand.MAIN_HAND);
            if (result.shouldSwing()) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
        pendingAction = PendingAction.NONE;
        clearPath();
        status = CommandStatus.TARGET_SELECTED;
        resetProgressTracking();
        return true;
    }

    private static void openDoorAhead(Player player, ClientLevel level, BlockPos nextNode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || !BlockPathfinder.isClosedWoodenDoor(level, nextNode)
                || player.distanceToSqr(Vec3.atCenterOf(nextNode)) > 6.25D) {
            return;
        }

        BlockHitResult doorHit = new BlockHitResult(
                Vec3.atCenterOf(nextNode),
                Direction.UP,
                nextNode,
                false
        );
        InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, doorHit);
        if (result.shouldSwing()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void facePlayerToward(Player player, Entity target) {
        double deltaX = target.getX() - player.getX();
        double deltaZ = target.getZ() - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        player.setYRot(Mth.wrapDegrees(yaw));
        player.yRotO = player.getYRot();
    }

    private static void clearPath() {
        path.clear();
        destination = null;
    }

    private static int horizontalBlockDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }

    private static void resetProgressTracking() {
        lastProgressPosition = null;
        stuckTicks = 0;
        replanCooldown = 0;
    }

    private static void clearMovement(Input input) {
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.left = false;
        input.right = false;
        input.up = false;
        input.down = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public enum CommandStatus {
        IDLE,
        MOVING,
        TARGET_SELECTED,
        UNREACHABLE
    }

    private enum PendingAction {
        NONE,
        ATTACK,
        INTERACT
    }
}
