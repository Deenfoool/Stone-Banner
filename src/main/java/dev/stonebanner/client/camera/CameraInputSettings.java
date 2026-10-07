package dev.stonebanner.client.camera;

/** Pure camera input math shared by captured-mouse and screen-drag rotation. */
public final class CameraInputSettings {
    private CameraInputSettings() {
    }

    public static float yaw(float current, double delta, double sensitivity) {
        double angle = (current + delta * sensitivity) % 360.0;
        return (float) (angle >= 180 ? angle - 360 : angle < -180 ? angle + 360 : angle);
    }

    public static float pitch(float current, double delta, double sensitivity, boolean invert) {
        return (float) Math.max(-15, Math.min(80, current + delta * sensitivity * (invert ? -1 : 1)));
    }

    public static double zoom(double current, double scroll, double sensitivity) {
        return Math.max(2, Math.min(24, current - scroll * sensitivity));
    }
}
