package dev.stonebanner.citizen;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import java.util.*;

/** Loaded, reachable beds only. Travelling to a bed is not physiological sleep. */
public final class CitizenSleepController {
    private static final Map<ServerLevel, CitizenBedReservations> RESERVATIONS = new WeakHashMap<>();
    private final HumanNpcEntity owner;
    private BlockPos bed;
    private boolean seeking;
    private long retryAt, routeUntil;
    public CitizenSleepController(HumanNpcEntity owner) { this.owner = owner; }
    public boolean isSeeking() { return seeking; }
    public boolean engaged() { return bed != null; }
    public Optional<BlockPos> bed() { return Optional.ofNullable(bed); }
    private CitizenBedReservations reservations(ServerLevel level) { return RESERVATIONS.computeIfAbsent(level, ignored -> new CitizenBedReservations()); }

    private boolean valid(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.dimensionType().bedWorks() || !owner.citizenData().canTravelTo(pos)) return false;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) return false;
        var foot = pos.relative(state.getValue(BedBlock.FACING).getOpposite());
        if (!level.hasChunkAt(foot)) return false;
        var other = level.getBlockState(foot);
        if (other.getBlock() != state.getBlock() || other.getValue(BedBlock.PART) != BedPart.FOOT
                || other.getValue(BedBlock.FACING) != state.getValue(BedBlock.FACING)) return false;
        boolean ownSleep = owner.isSleeping() && owner.getSleepingPos().filter(pos::equals).isPresent();
        if (state.getValue(BedBlock.OCCUPIED) && !ownSleep) return false;
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(2), e -> e != owner && e.isSleeping()
                && e.getSleepingPos().filter(pos::equals).isPresent()).isEmpty();
    }

    private boolean withinReach(ServerLevel level, BlockPos pos) {
        if (owner.distanceToSqr(Vec3.atCenterOf(pos)) > 2.5 * 2.5) return false;
        var hit = level.clip(new ClipContext(owner.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() == HitResult.Type.MISS) return true;
        var state = level.getBlockState(pos);
        return hit.getBlockPos().equals(pos) || hit.getBlockPos().equals(pos.relative(state.getValue(BedBlock.FACING).getOpposite()));
    }

    public void cancel(boolean stopMovement) {
        BlockPos old = bed;
        boolean ownRoute = seeking && owner.commandController().movementState() == CitizenBrainState.SLEEP;
        bed = null; seeking = false; retryAt = 0;
        if (owner.level() instanceof ServerLevel level && old != null) reservations(level).release(old, owner.getUUID());
        if (owner.isSleeping()) {
            // A saved sleeping position may now contain another sleeper after chunk reload.
            var sleepingPos = owner.getSleepingPos();
            boolean otherSleeper = sleepingPos.isPresent() && !owner.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(sleepingPos.get()).inflate(2), e -> e != owner && e.isSleeping()
                            && e.getSleepingPos().equals(sleepingPos)).isEmpty();
            owner.stopSleeping();
            if (otherSleeper && sleepingPos.isPresent()) {
                var state = owner.level().getBlockState(sleepingPos.get());
                if (state.getBlock() instanceof BedBlock) owner.level().setBlock(sleepingPos.get(),state.setValue(BedBlock.OCCUPIED,true),3);
            }
        }
        if (stopMovement && ownRoute) owner.commandController().stop();
    }

    public void sleepSecond() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return;
        if (bed != null) return;
        owner.commandController().stop(); owner.setBrainState(CitizenBrainState.SLEEP);
        if (level.getGameTime() < retryAt || !level.dimensionType().bedWorks()) return;
        retryAt = level.getGameTime() + 100;
        var candidates = new ArrayList<BlockPos>();
        int cx = owner.getBlockX() >> 4, cz = owner.getBlockZ() >> 4;
        for (int x = cx-2; x <= cx+2; x++) for (int z = cz-2; z <= cz+2; z++) {
            var chunk = level.getChunkSource().getChunkNow(x,z);
            if (chunk == null) continue;
            for (var entity : chunk.getBlockEntities().values()) {
                var pos = entity.getBlockPos();
                if (entity instanceof BedBlockEntity && Math.abs(pos.getY()-owner.getY()) <= 16
                        && owner.distanceToSqr(Vec3.atCenterOf(pos)) <= 32*32 && valid(level,pos)) candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> owner.distanceToSqr(Vec3.atCenterOf(p))));
        for (var pos : candidates.stream().limit(16).toList()) {
            if (!reservations(level).claim(pos, owner.getUUID(), level.getGameTime())) continue;
            bed = pos;
            if (withinReach(level,pos)) { lieDown(); return; }
            if (owner.citizenData().health().canMoveIndependently()) {
                var foot = pos.relative(level.getBlockState(pos).getValue(BedBlock.FACING).getOpposite());
                for (var anchor : List.of(pos,foot)) for (Direction direction : Direction.Plane.HORIZONTAL) {
                    var approach = anchor.relative(direction);
                    if (owner.citizenData().canTravelTo(approach) && BlockPathfinder.isWalkable(level,approach)
                            && owner.commandController().issueSystemMove(approach,CitizenBrainState.SLEEP)) {
                        seeking = true; routeUntil = level.getGameTime()+400; return;
                    }
                }
            }
            reservations(level).release(pos,owner.getUUID()); bed = null;
        }
        owner.setBrainState(CitizenBrainState.SLEEP); // Safe fallback: rest on the ground, retry bounded.
    }

    private void lieDown() {
        owner.commandController().stop(); seeking = false;
        owner.startSleeping(bed); owner.setBrainState(CitizenBrainState.SLEEP);
    }

    public void tick() {
        if (!(owner.level() instanceof ServerLevel level) || bed == null) return;
        var needs = owner.citizenData().needs();
        if (!owner.isAlive() || needs.isInCriticalDanger() || needs.isCriticallyHungry()
                || (owner.citizenData().health().needsRecovery() && needs.isHungry())
                || (!owner.citizenData().health().needsRecovery() && needs.fatigue() <= 25)
                || !valid(level,bed) || !reservations(level).claim(bed,owner.getUUID(),level.getGameTime())) {
            cancel(true); owner.setBrainState(CitizenBrainState.IDLE); return;
        }
        if (seeking) {
            if (withinReach(level,bed)) { lieDown(); return; }
            if (!owner.commandController().hasActiveCommand() || level.getGameTime() >= routeUntil
                    || owner.commandController().movementState() != CitizenBrainState.SLEEP) {
                cancel(true); retryAt = level.getGameTime()+100; owner.setBrainState(CitizenBrainState.SLEEP);
            }
        } else if (!owner.isSleeping()) { cancel(false); }
    }
}
