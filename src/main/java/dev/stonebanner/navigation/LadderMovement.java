package dev.stonebanner.navigation;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Shared movement math for staying attached to a ladder and stepping off its top. */
public final class LadderMovement {
    private static final double CENTER_EPSILON = 0.08D;
    private static final double VERTICAL_EPSILON = 0.08D;
    private static final double ATTACH_SPEED = 0.07D;
    private static final double TRAVERSE_SPEED = 0.14D;
    private static final double VERTICAL_SPEED = 0.20D;

    private LadderMovement() {
    }

    public static Vec3 horizontalDirection(Vec3 position, Vec3 target, Direction supportDirection) {
        double deltaX = target.x - position.x;
        double deltaZ = target.z - position.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance > CENTER_EPSILON) {
            return new Vec3(deltaX / horizontalDistance, 0.0D, deltaZ / horizontalDistance);
        }
        return new Vec3(supportDirection.getStepX(), 0.0D, supportDirection.getStepZ());
    }

    public static Vec3 citizenVelocity(Vec3 position, Vec3 target, Direction supportDirection,
                                       double speedMultiplier) {
        double multiplier = Math.max(0.0D, Math.min(1.5D, speedMultiplier));
        if (multiplier == 0.0D) {
            return Vec3.ZERO;
        }

        Vec3 horizontal = horizontalDirection(position, target, supportDirection);
        double deltaY = target.y - position.y;
        boolean changingLevel = Math.abs(deltaY) > VERTICAL_EPSILON;
        double horizontalSpeed = changingLevel ? ATTACH_SPEED : TRAVERSE_SPEED;
        double verticalSpeed = changingLevel ? Math.copySign(VERTICAL_SPEED, deltaY) : 0.0D;
        return new Vec3(
                horizontal.x * horizontalSpeed * multiplier,
                verticalSpeed * multiplier,
                horizontal.z * horizontalSpeed * multiplier
        );
    }
}
