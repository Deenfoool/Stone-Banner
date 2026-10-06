package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationType;
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

        assertTrue(DesignationController.click(new BlockPos(15, 75, 14)));
        assertEquals(2, DesignationController.completedClicks());
        assertEquals(DesignationController.SelectionStep.DEPTH, DesignationController.nextStep());

        DesignationController.updatePreview(new BlockPos(99, 64, 99));
        assertEquals(new BlockPos(15, 64, 14), DesignationController.selectionEnd().orElseThrow());

        assertTrue(DesignationController.undoSelectionStep());
        assertEquals(1, DesignationController.completedClicks());
        assertTrue(DesignationController.hasSelectionInProgress());

        assertTrue(DesignationController.undoSelectionStep());
        assertEquals(0, DesignationController.completedClicks());
        assertFalse(DesignationController.hasSelectionInProgress());

        assertFalse(DesignationController.undoSelectionStep());
        assertTrue(DesignationController.isActive());
    }
}
