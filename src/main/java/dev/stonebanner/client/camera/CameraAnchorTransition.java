package dev.stonebanner.client.camera;

import net.minecraft.world.phys.Vec3;

/**
 * Only interpolates a requested camera pivot. The caller MUST still sweep collisions from the
 * last validated anchor on every frame: never use a stale eased coordinate as a safe camera point.
 */
public final class CameraAnchorTransition {
    private Vec3 origin;
    private int elapsed, duration;

    public void start(Vec3 safeOrigin, int durationTicks) {
        origin = safeOrigin;
        elapsed = 0;
        duration = Math.max(1, durationTicks);
    }

    public void tick() {
        if (origin != null && ++elapsed >= duration) reset();
    }

    public boolean active() { return origin != null; }

    public Vec3 toward(Vec3 target, double partialTick) {
        if (origin == null) return target;
        double t = Math.max(0, Math.min(1, (elapsed + Math.max(0, Math.min(1, partialTick))) / duration));
        double smooth = t * t * (3 - 2 * t);
        return origin.lerp(target, smooth);
    }

    public void reset() { origin = null; elapsed = duration = 0; }
}
