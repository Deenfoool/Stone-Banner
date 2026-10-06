package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.designation.ExcavationAccessMode;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignationControllerTest {
    @AfterEach
    void resetController() {
        DesignationController.deactivate();
        DesignationController.setExcavationAccessMode(ExcavationAccessMode.AUTO);
    }

    @Test
    void selectionAdvancesToDepthAndRightClickRewindsOneStepAtATime() {
        DesignationController.activate(DesignationType.EXCAVATE);
        assertEquals(0, DesignationController.completedClicks());
        assertEquals(DesignationController.SelectionStep.LENGTH, DesignationController.nextStep());

        assertTrue(DesignationController.click(new BlockPos(10, 70, 10)));
        assertEquals(1, DesignationController.completedClicks());
        assertEquals(DesignationController.SelectionStep.WIDTH, DesignationController.nextStep());

        DesignationController.updatePreview(new BlockPos(15, 75, 14));
        assertEquals(new BlockPos(15, 70, 14), DesignationController.selectionEnd().orElseThrow());
        var footprint = DesignationController.previewDimensions().orElseThrow();
        assertEquals(6, footprint.sizeX());
        assertEquals(1, footprint.sizeY());
        assertEquals(5, footprint.sizeZ());

        assertTrue(DesignationController.click(new BlockPos(15, 75, 14)));
        assertEquals(2, DesignationController.completedClicks());
        assertEquals(DesignationController.SelectionStep.DEPTH, DesignationController.nextStep());

        DesignationController.updatePreview(new BlockPos(99, 64, 99));
        assertEquals(new BlockPos(15, 64, 14), DesignationController.selectionEnd().orElseThrow());
        var volume = DesignationController.previewDimensions().orElseThrow();
        assertEquals(6, volume.sizeX());
        assertEquals(7, volume.sizeY());
        assertEquals(5, volume.sizeZ());

        assertTrue(DesignationController.undoSelectionStep());
        assertEquals(1, DesignationController.completedClicks());
        assertTrue(DesignationController.hasSelectionInProgress());

        assertTrue(DesignationController.undoSelectionStep());
        assertEquals(0, DesignationController.completedClicks());
        assertFalse(DesignationController.hasSelectionInProgress());

        assertFalse(DesignationController.undoSelectionStep());
        assertTrue(DesignationController.isActive());
    }

    @Test
    void changingAccessModeClearsOnlyTheInProgressGeometry() {
        DesignationController.activate(DesignationType.EXCAVATE);
        DesignationController.click(new BlockPos(4, 70, 4));
        assertTrue(DesignationController.hasSelectionInProgress());

        DesignationController.setExcavationAccessMode(ExcavationAccessMode.LADDERS);

        assertEquals(ExcavationAccessMode.LADDERS, DesignationController.excavationAccessMode());
        assertFalse(DesignationController.hasSelectionInProgress());
        assertTrue(DesignationController.isActive());
    }

    @Test
    void accessModeCyclesAutoRampLadders() {
        DesignationController.setExcavationAccessMode(ExcavationAccessMode.AUTO);
        DesignationController.cycleExcavationAccessMode();
        assertEquals(ExcavationAccessMode.RAMP, DesignationController.excavationAccessMode());
        DesignationController.cycleExcavationAccessMode();
        assertEquals(ExcavationAccessMode.LADDERS, DesignationController.excavationAccessMode());
        DesignationController.cycleExcavationAccessMode();
        assertEquals(ExcavationAccessMode.AUTO, DesignationController.excavationAccessMode());
    }
}
