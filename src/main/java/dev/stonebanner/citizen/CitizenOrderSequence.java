package dev.stonebanner.citizen;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
                || owner.commandController().queuedMoveCount() > 0) return false;
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

        if (active.kind() == CitizenOrderQueue.Kind.WORK) {
            if (CitizenJobBoard.forLevel(level).job(activeJobId).isEmpty()) {
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
