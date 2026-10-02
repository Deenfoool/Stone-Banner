package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;

/** Shared client/server limits for one designation request. */
public final class DesignationLimits {
    public static final int MAX_BLOCKS_PER_REQUEST = 4096;
    public static final int MAX_AXIS_LENGTH = 64;

    private DesignationLimits() {
    }

    public static long volume(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return 0L;
        }
        long sizeX = axisLength(first.getX(), second.getX());
        long sizeY = axisLength(first.getY(), second.getY());
        long sizeZ = axisLength(first.getZ(), second.getZ());
        return sizeX * sizeY * sizeZ;
    }

    public static boolean isAllowed(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return false;
        }
        long sizeX = axisLength(first.getX(), second.getX());
        long sizeY = axisLength(first.getY(), second.getY());
        long sizeZ = axisLength(first.getZ(), second.getZ());
        return sizeX <= MAX_AXIS_LENGTH
                && sizeY <= MAX_AXIS_LENGTH
                && sizeZ <= MAX_AXIS_LENGTH
                && sizeX * sizeY * sizeZ <= MAX_BLOCKS_PER_REQUEST;
    }

    private static long axisLength(int first, int second) {
        return Math.abs((long) first - second) + 1L;
    }
}
