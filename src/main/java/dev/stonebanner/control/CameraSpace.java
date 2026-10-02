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

    public record MovementVector(float left, float forward) {
    }
}
