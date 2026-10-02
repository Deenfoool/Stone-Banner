package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignationLimitsTest {
    @Test
    void countsInclusiveCuboidVolume() {
        assertEquals(24L, DesignationLimits.volume(
                new BlockPos(0, 0, 0),
                new BlockPos(3, 2, 1)
        ));
    }

    @Test
    void acceptsMaximumVolumeBoundary() {
        assertTrue(DesignationLimits.isAllowed(
                new BlockPos(0, 0, 0),
                new BlockPos(15, 15, 15)
        ));
        assertEquals(4096L, DesignationLimits.volume(
                new BlockPos(0, 0, 0),
                new BlockPos(15, 15, 15)
        ));
    }

    @Test
    void rejectsTooLargeVolumeOrAxis() {
        assertFalse(DesignationLimits.isAllowed(
                new BlockPos(0, 0, 0),
                new BlockPos(16, 16, 16)
        ));
        assertFalse(DesignationLimits.isAllowed(
                new BlockPos(0, 0, 0),
                new BlockPos(64, 0, 0)
        ));
    }
}
