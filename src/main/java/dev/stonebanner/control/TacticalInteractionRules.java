package dev.stonebanner.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Shared client/server reach geometry. The camera's position never grants the hero interaction reach. */
public final class TacticalInteractionRules {
    private TacticalInteractionRules() {}
    public static boolean validHit(BlockPos block, Vec3 hit) {
        return validHit(block, hit, java.util.List.of(new AABB(0, 0, 0, 1, 1, 1)));
    }
    public static boolean validHit(BlockPos block, Vec3 hit, java.util.List<AABB> localShapes) {
        if (!Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)) return false;
        Vec3 local = hit.subtract(block.getX(), block.getY(), block.getZ());
        return localShapes.stream().anyMatch(box -> box.inflate(.001).contains(local));
    }
    public static BlockHitResult trace(Level level, Player player, Vec3 point) {
        return level.clip(new ClipContext(player.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
    }
    public static boolean visible(Level level, Player player, Vec3 point, BlockPos permittedBlock) {
        return visibleFrom(level, player, player.getEyePosition(), point, permittedBlock);
    }
    public static boolean visibleFrom(Level level, Player player, Vec3 eyes, Vec3 point, BlockPos permittedBlock) {
        var hit=level.clip(new ClipContext(eyes,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.MISS || permittedBlock!=null&&hit.getBlockPos().equals(permittedBlock);
    }
}
