package dev.stonebanner.citizen;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import dev.stonebanner.navigation.BlockPathfinder;

/**
 * Server-owned short-lived player-order sequence. Never navigates a fake NPC or persists an
 * executable order through reload. Stops on failure/preemption rather than skipping unsafe work.
 */
public final class CitizenOrderSequence {
    private static final long TARGET_TIMEOUT = 20L * 20L;
    private static final long WORK_TIMEOUT = 20L * 90L;
    private static final double ARRIVAL_SQR = 2.75D * 2.75D;

    private final HumanNpcEntity owner;
    private final CitizenOrderQueue pending = new CitizenOrderQueue();
    private CitizenOrderQueue.Entry active;
    private long activeStarted;
    private long activeJobId;
    private boolean starting;
    private String failure = "";
    private java.util.UUID commander;

    public CitizenOrderSequence(HumanNpcEntity owner) { this.owner = owner; }
    public boolean starting() { return starting; }
    public int pendingCount() { return pending.size(); }
    public boolean hasOrders() { return active != null || !pending.isEmpty(); }
    public String failure() { return failure; }
    public String preview() { return CitizenOrderQueue.encodePreview(active, pending.snapshot()); }

    /** Deliberately exclude containers, GUI blocks and modded block entities from NPC use. */
    public static boolean interactiveBlock(BlockState state) {
        var block = state.getBlock();
        return block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock;
    }

    private boolean permittedInteraction(ServerLevel level, BlockPos pos) {
        if (commander == null || !level.hasChunkAt(pos) || !owner.citizenData().canTravelTo(pos)
                || !interactiveBlock(level.getBlockState(pos))) return false;
        var player = level.getServer().getPlayerList().getPlayer(commander);
        return player != null && player.serverLevel() == level && player.isAlive() && !player.isSpectator()
                && owner.citizenData().canBeDirectedBy(commander) && level.mayInteract(player, pos)
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 256D * 256D;
    }

    private boolean inInteractionRange(ServerLevel level, BlockPos pos) {
        if (owner.distanceToSqr(Vec3.atCenterOf(pos)) > 3.25D * 3.25D) return false;
        var trace = level.clip(new ClipContext(owner.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner));
        return trace.getType() == HitResult.Type.MISS
                || trace instanceof BlockHitResult block && block.getBlockPos().equals(pos);
    }

    private boolean executeInteraction(ServerLevel level, BlockPos pos) {
        if (!permittedInteraction(level, pos) || !inInteractionRange(level, pos)) return false;
        var fake = FakePlayerFactory.getMinecraft(level);
        fake.setPos(owner.getX(), owner.getY(), owner.getZ());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var result = level.getBlockState(pos).use(level, fake, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) owner.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }

    private boolean approachInteraction(ServerLevel level, BlockPos pos) {
        int searched = 0;
        for (Direction side : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos stand = pos.relative(side).below(dy);
                if (!level.hasChunkAt(stand) || !owner.citizenData().canTravelTo(stand)
                        || !BlockPathfinder.isWalkable(level, stand)) continue;
                // At most four bounded A* searches per order, even for 64 selected citizens.
                if (++searched > 4) return false;
                if (BlockPathfinder.findPermittedPath(level, owner.blockPosition(), stand,
                        p -> level.hasChunkAt(p) && owner.citizenData().canTravelTo(p)).isEmpty()) continue;
                if (owner.issueCommand(new ActorCommand.MoveTo(stand))) return true;
            }
        }
        return false;
    }

    public void clear() {
        pending.clear();
        active = null;
        activeJobId = 0;
    }

    public boolean enqueue(CitizenOrderQueue.Entry entry, java.util.UUID issuedBy) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()
                || owner.citizenData().health().needsRecovery()
                || !owner.citizenData().health().canMoveIndependently()
                || owner.citizenData().returningToVillage()
                || owner.commandController().queuedMoveCount() > 0
                // Prevent another commander from silently taking over a running FIFO.
                || hasOrders() && !java.util.Objects.equals(commander, issuedBy)) return false;
        if (entry.block() != null && (!level.hasChunkAt(entry.block())
                || !owner.citizenData().canTravelTo(entry.block()))) return false;
        if (entry.target() != null) {
            Entity entity = level.getEntity(entry.target());
            if (entity == null || !entity.isAlive() || entity == owner
                    || !level.hasChunkAt(entity.blockPosition())
                    || !owner.citizenData().canTravelTo(entity.blockPosition())
                    || entry.kind() == CitizenOrderQueue.Kind.ATTACK
                        && !(entity instanceof net.minecraft.world.entity.monster.Monster)) return false;
        }
        if (entry.kind() == CitizenOrderQueue.Kind.WORK && availableJob(level, entry) == null) return false;
        if (entry.kind() == CitizenOrderQueue.Kind.INTERACT) {
            var player = level.getServer().getPlayerList().getPlayer(issuedBy);
            if (player == null || player.serverLevel() != level || !level.mayInteract(player, entry.block())
                    || !interactiveBlock(level.getBlockState(entry.block()))) return false;
        }
        // An unbounded manual follow/attack or a currently reserved autonomous work job must be
        // explicitly replaced with a normal order first; append never hijacks it silently.
        if (active == null && (owner.commandController().movementState() == CitizenBrainState.FOLLOW
                && owner.commandController().hasActiveCommand() || owner.workController().hasActiveJob()))
            return false;
        if (!pending.offer(entry)) return false;
        commander = issuedBy;
        failure = "";
        if (active == null && !owner.commandController().hasActiveCommand()) advance(level);
        return true;
    }

    public void tick() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) { clear(); return; }
        // This sequencer is optional. Never stop autonomous work/food/sleep for an NPC
        // without an explicitly queued player order.
        if (!hasOrders()) return;
        // The emergency AI owns its movement: discard queued player work without stopping
        // FLEE / DEFEND / RETURN_HOME, which would defeat the safety preemption.
        var movement = owner.commandController().movementState();
        if (movement == CitizenBrainState.FLEE || movement == CitizenBrainState.DEFEND
                || movement == CitizenBrainState.RETURN_HOME) {
            clear();
            return;
        }
        if (owner.citizenData().health().needsRecovery()
                || !owner.citizenData().health().canMoveIndependently()
                || owner.citizenData().returningToVillage()) { abort("preempted"); return; }
        if (active == null) {
            if (!owner.commandController().hasActiveCommand() && !owner.workController().hasActiveJob()
                    && !pending.isEmpty()) advance(level);
            return;
        }

        if (active.kind() == CitizenOrderQueue.Kind.MOVE) {
            if (owner.commandController().status() == CitizenCommandController.CommandStatus.UNREACHABLE) {
                abort("no_path"); return;
            }
            if (!owner.commandController().hasActiveCommand()) finish(level);
            return;
        }

        if (active.kind() == CitizenOrderQueue.Kind.INTERACT) {
            if (!permittedInteraction(level, active.block())) { abort("invalid_target"); return; }
            if (level.getGameTime() - activeStarted >= TARGET_TIMEOUT
                    || owner.commandController().status() == CitizenCommandController.CommandStatus.UNREACHABLE) {
                abort("interaction_unreachable"); return;
            }
            if (!owner.commandController().hasActiveCommand()) {
                if (!executeInteraction(level, active.block())) { abort("interaction_unreachable"); return; }
                finish(level);
            }
            return;
        }

        if (active.kind() == CitizenOrderQueue.Kind.WORK) {
            if (CitizenJobBoard.forLevel(level).job(activeJobId).isEmpty()) {
                // Job cancellation by a designation change is not a successful mining order.
                if (!level.hasChunkAt(active.block()) || !level.getBlockState(active.block()).isAir()) {
                    abort("work_blocked");
                    return;
                }
                finish(level); return;
            }
            if (owner.workController().currentJob().stream().noneMatch(job -> job.id() == activeJobId)
                    || level.getGameTime() - activeStarted >= WORK_TIMEOUT) {
                abort("work_blocked");
            }
            return;
        }

        Entity target = level.getEntity(active.target());
        if (active.kind() == CitizenOrderQueue.Kind.ATTACK && (target == null || !target.isAlive())) {
            owner.commandController().stop();
            finish(level);
            return;
        }
        if (target == null || !target.isAlive()
                || !owner.citizenData().canTravelTo(target.blockPosition())
                || level.getGameTime() - activeStarted >= TARGET_TIMEOUT
                || owner.commandController().status() == CitizenCommandController.CommandStatus.UNREACHABLE) {
            abort("target_unreachable"); return;
        }
        if (active.kind() == CitizenOrderQueue.Kind.FOLLOW
                && owner.distanceToSqr(target) <= ARRIVAL_SQR) {
            owner.commandController().stop();
            finish(level);
        }
    }

    private CitizenJob availableJob(ServerLevel level, CitizenOrderQueue.Entry entry) {
        if (entry.kind() != CitizenOrderQueue.Kind.WORK) return null;
        // A queued work order may only claim an EXISTING designated job. It cannot publish a
        // new mining target or silently enlarge the player's permitted excavation zone.
        return CitizenJobBoard.forLevel(level).availableJobs(owner.getUUID(), level.getGameTime())
                .stream().filter(job -> job.target().equals(entry.block())
                        && (job.workType() == WorkType.MINING || job.workType() == WorkType.FORESTRY)
                        && job.canBeDoneBy(owner.citizenData())
                        && WorkTargetRules.isValid(job.workType(), level, job.target()))
                .findFirst().orElse(null);
    }

    private boolean begin(ServerLevel level, CitizenOrderQueue.Entry entry) {
        if (entry.block() != null && (!level.hasChunkAt(entry.block())
                || !owner.citizenData().canTravelTo(entry.block()))) return false;
        starting = true;
        try {
            switch (entry.kind()) {
                case MOVE -> {
                    return owner.issueCommand(new ActorCommand.MoveTo(entry.block()));
                }
                case FOLLOW, ATTACK -> {
                    Entity entity = level.getEntity(entry.target());
                    if (entity == null || !entity.isAlive() || entity == owner
                            || !level.hasChunkAt(entity.blockPosition())
                            || !owner.citizenData().canTravelTo(entity.blockPosition())) return false;
                    if (entry.kind() == CitizenOrderQueue.Kind.ATTACK) {
                        return entity instanceof net.minecraft.world.entity.monster.Monster
                                && owner.issueCommand(new ActorCommand.EntityAction(
                                        entity.getId(), ActorCommand.EntityActionType.ATTACK));
                    }
                    return owner.issueCommand(new ActorCommand.FollowEntity(entity.getId(), 2));
                }
                case WORK -> {
                    CitizenJob job = availableJob(level, entry);
                    return job != null && owner.workController().assign(job);
                }
                case INTERACT -> {
                    if (!permittedInteraction(level, entry.block())) return false;
                    return inInteractionRange(level, entry.block()) || approachInteraction(level, entry.block());
                }
            }
        } finally {
            starting = false;
        }
        return false;
    }

    private void advance(ServerLevel level) {
        CitizenOrderQueue.Entry next = pending.poll();
        if (next == null) { active = null; activeJobId = 0; return; }
        if (!begin(level, next)) { abort("invalid_target"); return; }
        active = next;
        activeJobId = next.kind() == CitizenOrderQueue.Kind.WORK
                ? owner.workController().currentJob().map(CitizenJob::id).orElse(0L) : 0L;
        activeStarted = level.getGameTime();
        if (active.kind() == CitizenOrderQueue.Kind.WORK && activeJobId == 0) abort("work_blocked");
    }

    private void finish(ServerLevel level) {
        active = null;
        activeJobId = 0;
        // On the next tick start the next instruction, never more than one new A* query per tick.
    }

    private void abort(String reason) {
        failure = reason;
        if (owner.level() instanceof ServerLevel level && commander != null) {
            var commanderPlayer = level.getServer().getPlayerList().getPlayer(commander);
            if (commanderPlayer != null && commanderPlayer.serverLevel() == level)
                commanderPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.stonebanner.order_interrupted", owner.getDisplayName(),
                        net.minecraft.network.chat.Component.translatable("message.stonebanner.order_reason." + reason)), true);
        }
        clear();
        owner.commandController().stop();
        if (owner.workController().currentJob().isPresent()) owner.workController().interrupt(true);
    }
}
