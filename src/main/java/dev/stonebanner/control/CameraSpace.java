package dev.stonebanner.control;

public final class CameraSpace {
    private CameraSpace() {
    }

    public static MovementVector rotateMovement(float left, float forward, float deltaDegrees) {
        double radians = Math.toRadians(deltaDegrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);

        return new MovementVector(
                left * cos - forward * sin,
                left * sin + forward * cos
        );
    }

    public static MovementVector worldToLocal(double worldX, double worldZ, float playerYawDegrees) {
        double radians = Math.toRadians(playerYawDegrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);

        return new MovementVector(
                (float) (worldX * cos + worldZ * sin),
                (float) (-worldX * sin + worldZ * cos)
        );
    }

    /** Converts a horizontal world direction into Minecraft's yaw convention. */
    public static float yawForWorldDirection(double worldX, double worldZ) {
        float yaw = (float) Math.toDegrees(Math.atan2(worldZ, worldX)) - 90.0F;
        float wrapped = yaw % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record MovementVector(float left, float forward) {
    }
}
