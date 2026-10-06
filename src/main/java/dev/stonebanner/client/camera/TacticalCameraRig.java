package dev.stonebanner.client.camera;

import net.minecraft.world.phys.Vec3;

/** Independent tactical pivot; actor movement never updates it automatically. */
public final class TacticalCameraRig {
    private Vec3 previous, desired;
    public void reset(Vec3 anchor) { previous = desired = anchor; }
    public boolean initialized() { return desired != null; }
    public Vec3 desired() { return desired; }
    public void tick() { previous = desired; }
    public Vec3 interpolated(double partial) { return previous.lerp(desired, Math.max(0, Math.min(1, partial))); }
    public void pan(double left, double forward, double up, float yaw, double speed) {
        if (desired == null) return;
        double radians = Math.toRadians(yaw);
        Vec3 direction = new Vec3(Math.cos(radians)*left - Math.sin(radians)*forward, up,
                Math.sin(radians)*left + Math.cos(radians)*forward);
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        desired = desired.add(direction.scale(speed));
    }
}
