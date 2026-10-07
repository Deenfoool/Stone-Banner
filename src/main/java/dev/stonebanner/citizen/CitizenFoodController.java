package dev.stonebanner.citizen;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Food is removed atomically on arrival, never promised or duplicated while travelling. */
public final class CitizenFoodController {
    private final HumanNpcEntity owner;
    private BlockPos target;
    private boolean seeking;
    private long retryAt;

    public CitizenFoodController(HumanNpcEntity owner) { this.owner = owner; }
    public boolean isSeeking() { return seeking; }

    public void cancel(boolean stopMovement) {
        boolean ownRoute = target != null && owner.commandController().movementState() == CitizenBrainState.EAT;
        target = null;
        seeking = false;
        retryAt = 0;
        if (stopMovement && ownRoute) owner.commandController().stop();
    }

    private boolean edible(ItemStack stack) {
        var food = stack.getFoodProperties(owner);
        return food != null && food.getNutrition() > 0;
    }

    /** Called by the needs decision, at most once per second. */
    public void eatSecond() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return;
        seeking = true;
        if (target != null) return; // Critical hunger must not restart our own approach every second.
        owner.commandController().stop();
        owner.setBrainState(CitizenBrainState.EAT);
        var personal = owner.citizenData().inventory().consumeFood(owner);
        if (personal.isPresent()) {
            owner.citizenData().needs().eat(personal.get().hungerRelief());
            return;
        }
        if (level.getGameTime() < retryAt) return;
        retryAt = level.getGameTime() + 40;
        int attempts = 0;
        for (BlockPos source : StorageData.forLevel(level).containersWithItem(level, owner.blockPosition(), this::edible, 64)) {
            if (!owner.citizenData().canTravelTo(source)) continue;
            if (++attempts > 8) break;
            if (owner.distanceToSqr(Vec3.atCenterOf(source)) <= 2.75 * 2.75 && visible(level, owner.getEyePosition(), source)) {
                target = source.immutable();
                return; // An immobilized citizen can still eat within reach; no walking required.
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos approach = source.relative(direction);
                if (!owner.citizenData().canTravelTo(approach) || !BlockPathfinder.isWalkable(level, approach)
                        || !visible(level, BlockPathfinder.waypoint(level, approach).add(0, owner.getEyeHeight(), 0), source)) continue;
                if (owner.commandController().issueSystemMove(approach, CitizenBrainState.EAT)) {
                    target = source.immutable();
                    owner.setBrainState(CitizenBrainState.EAT);
                    return;
                }
            }
        }
        owner.setBrainState(CitizenBrainState.EAT);
    }

    public void tick() {
        if (!seeking || !(owner.level() instanceof ServerLevel level)) return;
        var needs = owner.citizenData().needs();
        if (!owner.isAlive() || !needs.isHungry() || needs.isInCriticalDanger()
                || (!needs.isCriticallyHungry() && needs.isCriticallyTired())) {
            cancel(true);
            if (!owner.commandController().hasActiveCommand()) owner.setBrainState(CitizenBrainState.IDLE);
            return;
        }
        if (target == null) return;
        if (!level.hasChunkAt(target) || !owner.citizenData().canTravelTo(target)) {
            cancel(true);
            retryAt = level.getGameTime() + 40;
            return;
        }
        if (owner.distanceToSqr(Vec3.atCenterOf(target)) <= 2.75 * 2.75 && visible(level, owner.getEyePosition(), target)) {
            owner.commandController().stop();
            var extracted = StorageData.forLevel(level).extractAt(level, target, this::edible, 1);
            for (ItemStack portion : extracted.stacks()) {
                var properties = portion.getFoodProperties(owner);
                ItemStack remainder = portion.finishUsingItem(level, owner);
                owner.citizenData().needs().eat(properties.getNutrition() * 5.0);
                // Bowls/bottles and modded food remainders remain real items, even with a full bag.
                if (remainder != null && !remainder.isEmpty()) {
                    var overflow = owner.citizenData().inventory().add(remainder);
                    if (!overflow.isEmpty()) owner.spawnAtLocation(overflow);
                }
            }
            target = null;
            owner.setBrainState(CitizenBrainState.EAT);
            retryAt = level.getGameTime() + 40;
        } else if (!owner.commandController().hasActiveCommand()) {
            target = null;
            retryAt = level.getGameTime() + 40;
            owner.setBrainState(CitizenBrainState.EAT);
        }
    }

    private boolean visible(ServerLevel level, Vec3 eyes, BlockPos source) {
        var hit = level.clip(new ClipContext(eyes, Vec3.atCenterOf(source), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(source);
    }
}
