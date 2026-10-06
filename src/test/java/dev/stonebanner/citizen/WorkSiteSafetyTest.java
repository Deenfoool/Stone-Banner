package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkSiteSafetyTest {
    @Test void includesEntityStandingOnFullBlock() {
        var volume = WorkSiteSafety.occupiedVolume(new BlockPos(10, 63, -5), Shapes.block());
        assertTrue(volume.intersects(new AABB(10.2, 64, -4.8, 10.8, 65.8, -4.2)));
    }
    @Test void tallFenceShapeProtectsEntityAboveUnitCube() {
        var volume = WorkSiteSafety.occupiedVolume(new BlockPos(0, 63, 0), Shapes.box(.25, 0, .25, .75, 1.5, .75));
        assertTrue(volume.intersects(new AABB(.2, 64.5, .2, .8, 66.3, .8)));
    }
    @Test void slabUsesItsRealSupportingHeight() {
        var volume = WorkSiteSafety.occupiedVolume(BlockPos.ZERO, Shapes.box(0, 0, 0, 1, .5, 1));
        assertTrue(volume.intersects(new AABB(.2, .5, .2, .8, 2.3, .8)));
        assertFalse(volume.intersects(new AABB(.2, .8, .2, .8, 2.6, .8)));
    }
    @Test void distantEntityDoesNotBlockWork() {
        assertFalse(WorkSiteSafety.occupiedVolume(BlockPos.ZERO, Shapes.block()).intersects(new AABB(3, 1, 3, 4, 3, 4)));
    }
    @Test void emptyShapeHasConservativeFallback() {
        assertTrue(WorkSiteSafety.occupiedVolume(BlockPos.ZERO, Shapes.empty()).contains(.5, .5, .5));
    }
    @Test void unknownStatusDoesNotInventAProblem() {
        assertEquals(WorkBlockReason.NONE, WorkBlockReason.byId(-1));
        assertEquals(WorkBlockReason.NONE, WorkBlockReason.byId(100));
    }
}
