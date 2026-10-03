package dev.stonebanner.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraSpaceTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void leavesMovementUnchangedWhenCameraAndPlayerFaceSameDirection() {
        CameraSpace.MovementVector result = CameraSpace.rotateMovement(0.25F, 1.0F, 0.0F);

        assertEquals(0.25F, result.left(), EPSILON);
        assertEquals(1.0F, result.forward(), EPSILON);
    }

    @Test
    void rotatesForwardInputWithCameraYaw() {
        CameraSpace.MovementVector result = CameraSpace.rotateMovement(0.0F, 1.0F, 90.0F);

        assertEquals(-1.0F, result.left(), EPSILON);
        assertEquals(0.0F, result.forward(), EPSILON);
    }

    @Test
    void preservesDiagonalMovementMagnitude() {
        CameraSpace.MovementVector result = CameraSpace.rotateMovement(1.0F, 1.0F, 37.0F);
        double originalMagnitude = Math.hypot(1.0F, 1.0F);
        double rotatedMagnitude = Math.hypot(result.left(), result.forward());

        assertEquals(originalMagnitude, rotatedMagnitude, EPSILON);
    }

    @Test
    void convertsWorldDirectionIntoPlayerLocalInput() {
        CameraSpace.MovementVector local = CameraSpace.worldToLocal(1.0D, 0.0D, 90.0F);

        assertEquals(0.0F, local.left(), EPSILON);
        assertEquals(-1.0F, local.forward(), EPSILON);
    }

    @Test
    void worldToLocalAndRotationAreInverseOperations() {
        CameraSpace.MovementVector local = CameraSpace.worldToLocal(0.6D, 0.8D, 37.0F);
        CameraSpace.MovementVector world = CameraSpace.rotateMovement(local.left(), local.forward(), 37.0F);

        assertEquals(0.6F, world.left(), EPSILON);
        assertEquals(0.8F, world.forward(), EPSILON);
    }

    @Test
    void convertsCardinalWorldDirectionsToMinecraftYaw() {
        assertEquals(0.0F, CameraSpace.yawForWorldDirection(0.0D, 1.0D), EPSILON);
        assertEquals(-90.0F, CameraSpace.yawForWorldDirection(1.0D, 0.0D), EPSILON);
        assertEquals(90.0F, CameraSpace.yawForWorldDirection(-1.0D, 0.0D), EPSILON);
        assertEquals(-180.0F, CameraSpace.yawForWorldDirection(0.0D, -1.0D), EPSILON);
    }
}
