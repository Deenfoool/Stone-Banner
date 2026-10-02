package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;

/** Shared client/server limits for one designation request. */
public final class DesignationLimits {
    public static final int MAX_BLOCKS_PER_REQUEST = 4096;
    public static final int MAX_AXIS_LENGTH = 64;

    private DesignationLimits() {
    }

    public static long volume(BlockPos first, BlockPos second) {
        return dimensions(first, second).volume();
    }

    public static Dimensions dimensions(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return Dimensions.EMPTY;
        }
        return new Dimensions(
                axisLength(first.getX(), second.getX()),
                axisLength(first.getY(), second.getY()),
                axisLength(first.getZ(), second.getZ())
        );
    }

    public static boolean isAllowed(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return false;
        }
        Dimensions dimensions = dimensions(first, second);
        return dimensions.sizeX() <= MAX_AXIS_LENGTH
                && dimensions.sizeY() <= MAX_AXIS_LENGTH
                && dimensions.sizeZ() <= MAX_AXIS_LENGTH
                && dimensions.volume() <= MAX_BLOCKS_PER_REQUEST;
    }

    private static long axisLength(int first, int second) {
        return Math.abs((long) first - second) + 1L;
    }

    public record Dimensions(long sizeX, long sizeY, long sizeZ) {
        private static final Dimensions EMPTY = new Dimensions(0L, 0L, 0L);

        public long volume() {
            try {
                return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
    }
}
