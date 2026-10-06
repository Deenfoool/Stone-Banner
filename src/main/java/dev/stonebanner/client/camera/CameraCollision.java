package dev.stonebanner.client.camera;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Function;

/** Conservative swept camera volume. A clear endpoint is insufficient: the entire route must be clear. */
public final class CameraCollision {
    private static final double STEP = .5;
    private static final double SKIN = .02;
    public static final double MAX_ANCHOR_TRAVEL = 96;
    private CameraCollision() {}
    public record Result(Vec3 position, boolean blockedStart) {}

    /** Radius encloses the near clipping rectangle at any yaw/pitch, including wide aspect ratios. */
    public static double radius(double fovDegrees, double aspect) {
        double fov = Math.max(1, Math.min(170, fovDegrees));
        return Math.max(.12, .05 * Math.tan(Math.toRadians(fov) / 2) * Math.sqrt(1 + aspect * aspect) + .04);
    }

    public static Result sweep(Vec3 start, Vec3 requested, double radius,
                               Function<AABB, List<AABB>> obstacles) {
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Camera radius");
        double padding = radius + SKIN;
        for (var box : obstacles.apply(new AABB(start, start).inflate(padding)))
            if (box.inflate(padding).contains(start)) return new Result(start, true);
        Vec3 delta = requested.subtract(start);
        double length = delta.length();
        if (length < 1e-8) return new Result(start, false);
        // Focus cannot initiate an arbitrarily expensive sweep across the whole world.
        if (length > MAX_ANCHOR_TRAVEL) { delta = delta.scale(MAX_ANCHOR_TRAVEL / length); length = MAX_ANCHOR_TRAVEL; }
        Vec3 direction = delta.normalize();
        Vec3 current = start;
        int steps = (int) Math.ceil(length / STEP);
        for (int i = 1; i <= steps; i++) {
            Vec3 end = start.add(direction.scale(Math.min(length, i * STEP)));
            Vec3 closest = null;
            for (var box : obstacles.apply(new AABB(current, end).inflate(padding))) {
                var expanded = box.inflate(padding);
                if (expanded.contains(current)) return new Result(current, false);
                var hit = expanded.clip(current, end).orElse(null);
                if (hit != null && (closest == null || current.distanceToSqr(hit) < current.distanceToSqr(closest))) closest = hit;
            }
            if (closest != null) return new Result(closest.subtract(direction.scale(.002)), false);
            current = end;
        }
        return new Result(current, false);
    }
}
