package dev.stonebanner.client.control;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;

/** Projects the free tactical cursor through the active camera into the world. */
public final class WorldCursor {
    private static final double MAX_REACH = 96.0D;
    private static Integer hoveredEntityId;
    private static Vec3 hoveredLocation;

    private WorldCursor() {
    }

    public static Optional<HitResult> pick(Minecraft minecraft, double mouseX, double mouseY,
                                           int screenWidth, int screenHeight) {
        return pick(minecraft, mouseX, mouseY, screenWidth, screenHeight, false);
    }

    public static Optional<HitResult> pick(Minecraft minecraft, double mouseX, double mouseY,
                                           int screenWidth, int screenHeight, boolean fluids) {
        if (minecraft.level == null || minecraft.player == null || screenWidth <= 0 || screenHeight <= 0
                || dev.stonebanner.client.camera.RpgCameraController.viewObstructed()) {
            hoveredEntityId = null;
            hoveredLocation = null;
            return Optional.empty();
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            hoveredEntityId = null;
            hoveredLocation = null;
            return Optional.empty();
        }

        double normalizedX = (2.0D * mouseX / screenWidth) - 1.0D;
        double normalizedY = 1.0D - (2.0D * mouseY / screenHeight);
        double aspectRatio = (double) screenWidth / screenHeight;
        double renderedFov = minecraft.gameRenderer.getFov(camera, minecraft.getFrameTime(), true);
        double tangent = Math.tan(Math.toRadians(renderedFov) * 0.5D);

        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vec3 direction = new Vec3(look)
                .add(new Vec3(left).scale(-normalizedX * tangent * aspectRatio))
                .add(new Vec3(up).scale(normalizedY * tangent))
                .normalize();

        Vec3 start = camera.getPosition();
        Vec3 end = start.add(direction.scale(MAX_REACH));
        BlockHitResult blockResult = minecraft.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                minecraft.player
        ));
        double blockDistance = blockResult.getType() == HitResult.Type.BLOCK
                ? start.distanceToSqr(blockResult.getLocation())
                : MAX_REACH * MAX_REACH;

        EntityHitResult entityResult = ProjectileUtil.getEntityHitResult(
                minecraft.player,
                start,
                end,
                new net.minecraft.world.phys.AABB(start, end).inflate(1.0D),
                entity -> isSelectable(entity, minecraft.player),
                MAX_REACH * MAX_REACH
        );
        if (entityResult != null && start.distanceToSqr(entityResult.getLocation()) < blockDistance) {
            hoveredEntityId = entityResult.getEntity().getId();
            hoveredLocation = entityResult.getLocation();
            return Optional.of(entityResult);
        }

        hoveredEntityId = null;
        hoveredLocation = blockResult.getType() == HitResult.Type.BLOCK ? blockResult.getLocation() : null;
        return blockResult.getType() == HitResult.Type.BLOCK ? Optional.of(blockResult) : Optional.empty();
    }

    public static Optional<Entity> hoveredEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (hoveredEntityId == null || minecraft.level == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(minecraft.level.getEntity(hoveredEntityId));
    }

    public static Optional<Vec3> hoveredLocation() {
        return Optional.ofNullable(hoveredLocation);
    }

    private static boolean isSelectable(Entity entity, Entity player) {
        return entity != player && !entity.isSpectator() && entity.isAlive() && entity.isPickable();
    }
}
