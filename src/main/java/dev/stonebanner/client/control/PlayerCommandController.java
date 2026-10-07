package dev.stonebanner.client.control;

import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.TacticalActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CameraSpace;
import dev.stonebanner.control.ControlMode;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.navigation.LadderMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Client-side executor for player commands. The command model itself is shared with Citizen AI. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class PlayerCommandController {
    private static final double ARRIVAL_DISTANCE = 0.55D;
    private static final int STUCK_REPLAN_TICKS = 30;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final Deque<BlockPos> path = new ArrayDeque<>();
    private static final dev.stonebanner.command.MoveOrderQueue queuedMoves = new dev.stonebanner.command.MoveOrderQueue();
    private static BlockPos destination;
    private static CommandStatus status = CommandStatus.IDLE;
    private static Vec3 lastProgressPosition;
    private static int stuckTicks;
    private static int replanCooldown;
    private static Integer selectedEntityId;
    private static PendingAction pendingAction = PendingAction.NONE;
    private static BlockPos attemptedControlledDoor;
    private static BlockHitResult pendingBlock;
    private static BlockPos actionGoal;
    private static Vec3 swimTarget;
    private static LocalPlayer commandPlayer;
    private static net.minecraft.resources.ResourceLocation commandDimension;

    private PlayerCommandController() {
    }

    public static void moveTo(BlockHitResult hit) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.player.isInWater()
                && !mc.level.getFluidState(hit.getBlockPos()).isEmpty()) {
            ensureWorld(mc); stopInternal(); swimTarget = hit.getLocation(); status = CommandStatus.MOVING; return;
        }
        Direction face = hit.getDirection();
        BlockPos requestedTarget = face == Direction.UP
                ? hit.getBlockPos().above()
                : hit.getBlockPos().relative(face);
        issue(new ActorCommand.MoveTo(requestedTarget));
    }

    public static boolean moving() { return !path.isEmpty() || swimTarget != null; }
    public static int queuedMoveCount() { return queuedMoves.size(); }

    public static void queueMoveTo(BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        ensureWorld(mc);
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return;
        BlockPos target = hit.getDirection() == Direction.UP ? hit.getBlockPos().above()
                : hit.getBlockPos().relative(hit.getDirection());
        boolean accepted = false;
        // Direct free-swimming and interaction commands are not mixed with ground waypoints.
        if (pendingBlock == null && pendingAction == PendingAction.NONE && swimTarget == null
                && mc.level.hasChunkAt(target) && !(mc.player.isInWater() && !mc.level.getFluidState(hit.getBlockPos()).isEmpty())) {
            if (!moving() && queuedMoves.size() == 0) {
                issue(new ActorCommand.MoveTo(target));
                accepted = status != CommandStatus.UNREACHABLE;
            } else accepted = queuedMoves.offer(target);
        }
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(accepted
                ? "message.stonebanner.hero_move_queued" : "message.stonebanner.hero_move_queue_rejected", queuedMoves.size()), true);
    }
    public static void approach(Vec3 point, BlockPos block, double reach) {
        var mc = Minecraft.getInstance(); ensureWorld(mc); stopInternal(); createInteractionPath(point, block, reach);
    }

    public static void contextAction(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || entity == minecraft.player) {
            return;
        }

        ActorCommand.EntityActionType action = entity instanceof Monster
                ? ActorCommand.EntityActionType.ATTACK
                : entity instanceof AbstractVillager
                ? ActorCommand.EntityActionType.INTERACT
                : ActorCommand.EntityActionType.SELECT;
        issue(new ActorCommand.EntityAction(entity.getId(), action));
    }

    public static void interactEntity(Entity entity) {
        issue(new ActorCommand.EntityAction(entity.getId(), ActorCommand.EntityActionType.INTERACT));
    }
    public static void attack(Entity entity) {
        issue(new ActorCommand.EntityAction(entity.getId(), ActorCommand.EntityActionType.ATTACK));
    }
    public static void interactBlock(BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ensureWorld(mc); stopInternal();
        pendingBlock = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos().immutable(), false);
        if (!executePendingBlock(mc.player)) createInteractionPath(hit.getLocation(), hit.getBlockPos(), mc.player.getBlockReach());
    }
    public static boolean isInteractiveBlock(BlockPos pos) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return false;
        var state = mc.level.getBlockState(pos); var block = state.getBlock();
        return state.getMenuProvider(mc.level, pos) != null || block instanceof net.minecraft.world.level.block.EntityBlock
                || block instanceof net.minecraft.world.level.block.BannerBlock
                || block instanceof dev.stonebanner.geology.ResearchTableBlock
                || block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock
                || block instanceof net.minecraft.world.level.block.ButtonBlock
                || block instanceof net.minecraft.world.level.block.LeverBlock
                || block instanceof net.minecraft.world.level.block.BedBlock;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopInternal(); commandPlayer = null; commandDimension = null;
    }
    private static void ensureWorld(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            stopInternal(); commandPlayer = null; commandDimension = null; return;
        }
        var dimension = mc.level.dimension().location();
        if (commandPlayer != mc.player || !dimension.equals(commandDimension)) stopInternal();
        commandPlayer = mc.player; commandDimension = dimension;
    }

    public static void stop() {
        issue(new ActorCommand.Stop());
    }

    /** Executes player intent without exposing mouse/input details to the command model. */
    public static void issue(ActorCommand command) {
        if (command == null) {
            return;
        }
        if (command instanceof ActorCommand.Stop) {
            stopInternal();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ensureWorld(minecraft);
        pendingBlock = null;
        if (command instanceof ActorCommand.MoveTo moveTo) {
            stopInternal();
            selectedEntityId = null;
            pendingAction = PendingAction.NONE;
            createPath(moveTo.target());
            return;
        }

        if (command instanceof ActorCommand.EntityAction entityAction) {
            Entity entity = minecraft.level.getEntity(entityAction.entityId());
            if (entity == null || !entity.isAlive() || entity == minecraft.player) {
                return;
            }
            stopInternal();
            issueEntityAction(minecraft.player, entity, entityAction.action());
        }
    }

    private static void issueEntityAction(Player player, Entity entity, ActorCommand.EntityActionType action) {
        selectedEntityId = entity.getId();
        if (action == ActorCommand.EntityActionType.ATTACK) {
            pendingAction = PendingAction.ATTACK;
        } else if (action == ActorCommand.EntityActionType.INTERACT) {
            pendingAction = PendingAction.INTERACT;
        } else {
            pendingAction = PendingAction.NONE;
        }

        facePlayerToward(player, entity);
        if (pendingAction == PendingAction.NONE) {
            clearPath();
            status = CommandStatus.TARGET_SELECTED;
            return;
        }
        if (!executePendingActionIfInRange(player, entity)) {
            createInteractionPath(entity.getBoundingBox().getCenter(), null, player.getEntityReach());
        }
    }

    private static void stopInternal() {
        queuedMoves.clear();
        pendingBlock = null;
        actionGoal = null; swimTarget = null;
        path.clear();
        destination = null;
        selectedEntityId = null;
        pendingAction = PendingAction.NONE;
        attemptedControlledDoor = null;
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

    public static int remainingPathNodes() {
        return path.size();
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
            stopInternal();
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

        if (!ClientConfig.ENFORCE_THIRD_PERSON.get() || !minecraft.player.isAlive()) { stop(); return; }
        if (!HeroInputController.commandMode() && ClientConfig.controlMode() == ControlMode.ACTION
                && HeroInputController.manualMovement()) {
            stop(); RpgCameraController.clearFocus(); return;
        }

        ensureWorld(minecraft);
        Input input = event.getInput();
        clearMovement(input);
        if (minecraft.screen != null && !(minecraft.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen)) return;
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (level == null) {
            stop();
            return;
        }

        BlockPos queuedTarget = queuedMoves.takeWhenIdle(moving());
        if (queuedTarget != null) {
            if (!level.hasChunkAt(queuedTarget)) {
                stopInternal(); status = CommandStatus.UNREACHABLE; return;
            }
            selectedEntityId = null;
            createPath(queuedTarget);
        }

        if (pendingBlock != null) {
            if (executePendingBlock(player)) return;
            if (path.isEmpty() && status != CommandStatus.UNREACHABLE)
                createInteractionPath(pendingBlock.getLocation(), pendingBlock.getBlockPos(), player.getBlockReach());
        }
        Entity actionTarget = selectedEntity().orElse(null);
        if (actionTarget != null && pendingAction != PendingAction.NONE) {
            facePlayerToward(player, actionTarget);
            if (executePendingActionIfInRange(player, actionTarget)) {
                clearMovement(input);
                return;
            }
            BlockPos movingTarget = BlockPos.containing(actionTarget.position());
            if ((actionGoal == null || horizontalBlockDistance(actionGoal, movingTarget) > 2)
                    && player.tickCount % 10 == 0)
                createInteractionPath(actionTarget.getBoundingBox().getCenter(), null, player.getEntityReach());
        }
        if (swimTarget != null) {
            if (!player.isInWater() || player.position().distanceToSqr(swimTarget) < .6) { swimTarget = null; status = CommandStatus.IDLE; return; }
            Vec3 delta = swimTarget.subtract(player.position());
            facePlayerTowardDirection(player, delta.x, delta.z);
            var movement = CameraSpace.worldToLocal(delta.x / Math.max(.01, delta.horizontalDistance()), delta.z / Math.max(.01, delta.horizontalDistance()), player.getYRot());
            input.leftImpulse = movement.left(); input.forwardImpulse = movement.forward();
            input.jumping = HeroInputController.jump() || swimTarget.y > player.getY() + .25;
            input.shiftKeyDown = HeroInputController.descend();
            updateProgressAndReplan(player, level); return;
        }
        if (path.isEmpty()) { return; }

        updateProgressAndReplan(player, level);
        BlockPos nextNode = path.peekFirst();
        if (nextNode == null) {
            if (status == CommandStatus.UNREACHABLE) return;
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
        double deltaY = moveTarget.y - player.getY();
        double deltaZ = moveTarget.z - player.getZ();
        boolean climbing = BlockPathfinder.isClimbable(level, nextNode)
                || BlockPathfinder.isClimbable(level, BlockPos.containing(player.position()))
                || BlockPathfinder.isClimbable(level, nextNode.below());
        double distance = climbing
                ? Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                : Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double waypointArrival = path.size() == 1 ? ARRIVAL_DISTANCE : 0.35D;
        if (distance <= waypointArrival) {
            path.removeFirst();
            attemptedControlledDoor = null;
            if (path.isEmpty()) {
                finishMovement();
                return;
            }
            nextNode = path.peekFirst();
            moveTarget = BlockPathfinder.waypoint(level, nextNode);
            deltaX = moveTarget.x - player.getX();
            deltaY = moveTarget.y - player.getY();
            deltaZ = moveTarget.z - player.getZ();
            climbing = BlockPathfinder.isClimbable(level, nextNode)
                    || BlockPathfinder.isClimbable(level, BlockPos.containing(player.position()))
                    || BlockPathfinder.isClimbable(level, nextNode.below());
            distance = climbing
                    ? Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                    : Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        }

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double worldX;
        double worldZ;
        if (climbing) {
            Direction climbDirection = BlockPathfinder.climbDirection(level, nextNode)
                    .or(() -> BlockPathfinder.climbDirection(level, BlockPos.containing(player.position())))
                    .or(() -> BlockPathfinder.climbDirection(level, BlockPos.containing(player.position()).below()))
                    .orElse(Direction.NORTH);
            Vec3 climbHorizontal = LadderMovement.horizontalDirection(player.position(), moveTarget, climbDirection);
            worldX = climbHorizontal.x;
            worldZ = climbHorizontal.z;
        } else if (horizontalDistance > 1.0E-4D) {
            worldX = deltaX / horizontalDistance;
            worldZ = deltaZ / horizontalDistance;
        } else {
            worldX = 0.0D;
            worldZ = 0.0D;
        }
        facePlayerTowardDirection(player, worldX, worldZ);
        CameraSpace.MovementVector movement = CameraSpace.worldToLocal(worldX, worldZ, player.getYRot());

        input.leftImpulse = Mth.clamp(movement.left(), -1.0F, 1.0F);
        input.forwardImpulse = Mth.clamp(movement.forward(), -1.0F, 1.0F);
        input.left = input.leftImpulse > 0.01F;
        input.right = input.leftImpulse < -0.01F;
        input.up = input.forwardImpulse > 0.01F;
        input.down = input.forwardImpulse < -0.01F;
        if (climbing) {
            input.jumping = deltaY > 0.15D;
            input.shiftKeyDown = deltaY < -0.15D;
        } else if (player.isInWater()) {
            input.jumping = HeroInputController.jump() || moveTarget.y > player.getY() + 0.25D;
            input.shiftKeyDown = HeroInputController.descend();
        } else {
            input.jumping = HeroInputController.jump() || player.onGround()
                    && (player.horizontalCollision || moveTarget.y > player.getY() + 0.35D);
            input.shiftKeyDown = false;
        }
    }

    private static void createInteractionPath(Vec3 point, BlockPos permittedBlock, double reach) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null) return;
        reach = Math.max(0, reach - .4); // Stop comfortably inside reach, not on a client/server timing boundary.
        BlockPos center = BlockPos.containing(point); actionGoal = center;
        var candidates = new java.util.ArrayList<BlockPos>();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 2; dy++) {
            var pos = center.offset(dx, dy, dz);
            if (!mc.level.hasChunkAt(pos) || !BlockPathfinder.isWalkable(mc.level, pos)) continue;
            Vec3 eyes = BlockPathfinder.waypoint(mc.level, pos).add(0, mc.player.getEyeHeight(), 0);
            if (eyes.distanceToSqr(point) <= reach * reach
                    && TacticalInteractionRules.visibleFrom(mc.level, mc.player, eyes, point, permittedBlock)) candidates.add(pos);
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> Vec3.atBottomCenterOf(pos).distanceToSqr(mc.player.position())));
        path.clear(); destination = null;
        int attempts = 0;
        for (var pos : candidates) {
            if (++attempts > 12) break;
            var route = BlockPathfinder.findPath(mc.level, mc.player.blockPosition(), pos);
            if (route.isPresent()) {
                destination = pos; path.addAll(route.get()); status = path.isEmpty() ? CommandStatus.TARGET_SELECTED : CommandStatus.MOVING;
                resetProgressTracking(); return;
            }
        }
        status = CommandStatus.UNREACHABLE;
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stonebanner.interaction_unreachable"), true);
    }
    private static boolean executePendingBlock(Player player) {
        var mc = Minecraft.getInstance(); if (pendingBlock == null || mc.level == null) return false;
        if (!mc.level.hasChunkAt(pendingBlock.getBlockPos())) { stopInternal(); return true; }
        double blockReach = Math.max(0, player.getBlockReach() - .4);
        if (!player.canReach(pendingBlock.getBlockPos(), 0)
                || player.getEyePosition().distanceToSqr(pendingBlock.getLocation()) > blockReach * blockReach
                || !TacticalInteractionRules.visible(mc.level, player, pendingBlock.getLocation(), pendingBlock.getBlockPos())) return false;
        facePlayerTowardDirection(player, pendingBlock.getLocation().x-player.getX(), pendingBlock.getLocation().z-player.getZ());
        sendAction(new TacticalActionPacket(TacticalActionPacket.Action.USE_BLOCK, -1, pendingBlock.getBlockPos(), pendingBlock.getDirection(), pendingBlock.getLocation()));
        pendingBlock = null; clearPath(); status = CommandStatus.IDLE; resetProgressTracking(); return true;
    }
    private static void sendAction(TacticalActionPacket packet) {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().selected));
        StoneBannerNetwork.sendTacticalAction(packet);
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
        attemptedControlledDoor = null;
        status = result.isPresent() ? (path.isEmpty() ? CommandStatus.IDLE : CommandStatus.MOVING)
                : CommandStatus.UNREACHABLE;
        if (result.isEmpty()) {
            queuedMoves.clear();
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
        if(swimTarget!=null){stopInternal();status=CommandStatus.UNREACHABLE;return;}
        if (pendingBlock != null) {
            createInteractionPath(pendingBlock.getLocation(), pendingBlock.getBlockPos(), Minecraft.getInstance().player.getBlockReach()); return;
        }
        var entity = selectedEntity().orElse(null);
        if (entity != null && pendingAction != PendingAction.NONE) {
            createInteractionPath(entity.getBoundingBox().getCenter(), null, Minecraft.getInstance().player.getEntityReach()); return;
        }
        if (destination == null) {
            return;
        }
        Optional<List<BlockPos>> result = BlockPathfinder.findPath(level, start, destination);
        path.clear();
        result.ifPresent(path::addAll);
        attemptedControlledDoor = null;
        status = result.isPresent() ? (path.isEmpty() ? CommandStatus.IDLE : CommandStatus.MOVING)
                : CommandStatus.UNREACHABLE;
        if (result.isPresent() && path.isEmpty()) {
            destination = null;
        }
        if (result.isEmpty()) {
            queuedMoves.clear();
            destination = null;
        }
        stuckTicks = 0;
        replanCooldown = REPLAN_COOLDOWN_TICKS;
    }

    private static void finishMovement() {
        clearPath();
        attemptedControlledDoor = null;
        status = selectedEntityId == null ? CommandStatus.IDLE : CommandStatus.TARGET_SELECTED;
        resetProgressTracking();
    }

    private static boolean executePendingActionIfInRange(Player player, Entity target) {
        var mc = Minecraft.getInstance();
        double entityReach = Math.max(0, player.getEntityReach() - .4);
        if (mc.level == null || pendingAction == PendingAction.NONE || !player.canReach(target, 0)
                || player.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter()) > entityReach * entityReach
                || !TacticalInteractionRules.visible(mc.level, player, target.getBoundingBox().getCenter(), null)) return false;
        facePlayerToward(player, target);
        clearPath(); attemptedControlledDoor = null; status = CommandStatus.TARGET_SELECTED;
        if (pendingAction == PendingAction.ATTACK) {
            if (player.getAttackStrengthScale(.5f) >= .9f && !player.isUsingItem()) {
                sendAction(new TacticalActionPacket(TacticalActionPacket.Action.ATTACK, target.getId(), BlockPos.ZERO, Direction.UP, Vec3.ZERO));
                player.swing(InteractionHand.MAIN_HAND); player.resetAttackStrengthTicker();
            }
            // Attack order persists until death, stop or a replacement order; vanilla attack cooldown still applies.
        } else {
            sendAction(new TacticalActionPacket(TacticalActionPacket.Action.INTERACT_ENTITY, target.getId(), BlockPos.ZERO, Direction.UP, Vec3.ZERO));
            pendingAction = PendingAction.NONE;
        }
        resetProgressTracking(); return true;
    }

    private static void openDoorAhead(Player player, ClientLevel level, BlockPos nextNode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) {
            return;
        }

        if (BlockPathfinder.isClosedWoodenDoor(level, nextNode)) {
            if (player.distanceToSqr(Vec3.atCenterOf(nextNode)) > 6.25D) {
                return;
            }
            attemptedControlledDoor = null;
            useBlock(player, nextNode);
            return;
        }

        if (!BlockPathfinder.isClosedIronDoor(level, nextNode)) {
            attemptedControlledDoor = null;
            return;
        }
        if (nextNode.equals(attemptedControlledDoor)) {
            return;
        }

        BlockPos control = BlockPathfinder.findNearbyDoorControl(level, nextNode).orElse(null);
        if (control == null || player.distanceToSqr(Vec3.atCenterOf(control)) > 25.0D) {
            return;
        }
        attemptedControlledDoor = nextNode.immutable();
        useBlock(player, control);
    }

    private static void useBlock(Player player, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || !(player instanceof LocalPlayer localPlayer)) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );
        InteractionResult result = minecraft.gameMode.useItemOn(localPlayer, InteractionHand.MAIN_HAND, hit);
        if (result.shouldSwing()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void facePlayerToward(Player player, Entity target) {
        double deltaX = target.getX() - player.getX();
        double deltaZ = target.getZ() - player.getZ();
        facePlayerTowardDirection(player, deltaX, deltaZ);
    }

    private static void facePlayerTowardDirection(Player player, double worldX, double worldZ) {
        if (worldX * worldX + worldZ * worldZ <= 1.0E-8D) {
            return;
        }
        float yaw = CameraSpace.yawForWorldDirection(worldX, worldZ);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
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
