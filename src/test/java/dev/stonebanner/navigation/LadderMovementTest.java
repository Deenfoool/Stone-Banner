package dev.stonebanner.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderMovementTest {
    @Test
    void ladderTopTransitionHasMatchingLandingForReverseEntry() {
        BlockPos ladder = new BlockPos(-990, 106, 844);

        assertEquals(new BlockPos(-990, 107, 845),
                BlockPathfinder.ladderTopExit(ladder, Direction.NORTH));
    }

    @Test
    void centeredClimberKeepsPressureTowardLadderSupport() {
        Vec3 direction = LadderMovement.horizontalDirection(
                new Vec3(4.5D, 10.0D, 7.5D),
                new Vec3(4.5D, 11.1D, 7.5D),
                Direction.WEST
        );

        assertEquals(-1.0D, direction.x);
        assertEquals(0.0D, direction.z);
    }

    @Test
    void topExitMovesDiagonallyUpAndTowardLanding() {
        Vec3 velocity = LadderMovement.citizenVelocity(
                new Vec3(4.5D, 10.0D, 7.5D),
                new Vec3(3.5D, 11.0D, 7.5D),
                Direction.WEST,
                1.0D
        );

        assertTrue(velocity.x < 0.0D);
        assertTrue(velocity.y > 0.0D);
        assertEquals(0.0D, velocity.z);
    }

    @Test
    void descendingLadderUsesControlledDownwardVelocity() {
        Vec3 velocity = LadderMovement.citizenVelocity(
                new Vec3(4.5D, 11.0D, 7.5D),
                new Vec3(4.5D, 10.1D, 7.5D),
                Direction.NORTH,
                0.5D
        );

        assertEquals(-0.1D, velocity.y, 1.0E-9D);
        assertTrue(velocity.z < 0.0D);
    }
}
