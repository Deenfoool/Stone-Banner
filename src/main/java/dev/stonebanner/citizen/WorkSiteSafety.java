package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Includes the actual collision shape and a small margin above a supporting surface. */
public final class WorkSiteSafety {
    private WorkSiteSafety() {}
    public static AABB occupiedVolume(BlockPos target, VoxelShape collision) {
        return (collision.isEmpty() ? new AABB(target) : collision.bounds().move(target)).inflate(.05);
    }
}
