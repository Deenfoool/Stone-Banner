package dev.stonebanner.client.control;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.navigation.BlockPathfinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class PathPreviewRenderer {
    private static ClientLevel priorWorld;
    private static List<BlockPos> priorRoute = List.of();
    private static boolean hadActiveRoute;
    private static long fadedAt;

    private PathPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<BlockPos> path = PlayerCommandController.pathSnapshot();
        BlockPos destination = PlayerCommandController.destination().orElse(null);
        BlockPos rejected=PlayerCommandController.rejectedGoal().orElse(null);
        Entity hoveredEntity = WorldCursor.hoveredEntity().orElse(null);
        Vec3 hoveredLocation = WorldCursor.hoveredLocation().orElse(null);
        Entity selectedEntity = PlayerCommandController.selectedEntity().orElse(null);
        Entity selectedCitizen = CitizenSelectionController.selected().orElse(null);
        boolean hasDesignationPreview = DesignationController.selectionStart().isPresent()
                && DesignationController.selectionEnd().isPresent();
        boolean hasExcavationOverlay = ExcavationOverlayState.hasPlans();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = net.minecraft.Util.getMillis();
        if (priorWorld != minecraft.level) {
            priorWorld = minecraft.level;
            priorRoute = List.of();
            hadActiveRoute = false;
            fadedAt = 0;
        }
        boolean routeVisible = ClientConfig.SHOW_PATH_PREVIEW.get();
        boolean markersVisible = ClientConfig.SHOW_ORDER_MARKERS.get();
        if (routeVisible && !path.isEmpty()) {
            priorRoute = path.size() > 96 ? List.copyOf(path.subList(0, 96)) : List.copyOf(path);
            hadActiveRoute = true;
        } else if (hadActiveRoute && path.isEmpty()) {
            hadActiveRoute = false;
            fadedAt = now;
        }
        boolean fading = routeVisible && !hadActiveRoute && !priorRoute.isEmpty()
                && now >= fadedAt && now - fadedAt < 700;
        boolean confirmation = dev.stonebanner.client.control.ContextFeedbackController.pulse(now).isPresent();
        if (!routeVisible && !markersVisible && !hasDesignationPreview && !confirmation) return;
        if (!fading && (!routeVisible || path.isEmpty()) && !markersVisible
                && !hasDesignationPreview && !confirmation) return;
        if (!fading && (!routeVisible || (path.isEmpty() && destination == null && rejected == null))
                && (!markersVisible || (hoveredEntity == null && selectedEntity == null
                && selectedCitizen == null && hoveredLocation == null && !hasExcavationOverlay))
                && !hasDesignationPreview && !confirmation) return;
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        if (routeVisible) {
            List<BlockPos> displayed = path.isEmpty() ? (fading ? priorRoute : List.of()) : path;
            float alpha = path.isEmpty() ? Math.max(0, 1f - (now - fadedAt) / 700f) : 0.64f;
            Vec3 previous = path.isEmpty() ? null : minecraft.player == null ? null
                    : minecraft.player.position().add(0, 0.08D, 0);
            for (BlockPos point : displayed) {
                Vec3 waypoint = BlockPathfinder.waypoint(minecraft.level, point).add(0, 0.10D, 0);
                if (previous != null) drawRouteSegment(poses, lines, previous, waypoint, alpha);
                previous = waypoint;
            }
        }
        if (routeVisible && destination != null) {
            Vec3 destinationPoint = minecraft.level == null
                    ? Vec3.atBottomCenterOf(destination)
                    : BlockPathfinder.waypoint(minecraft.level, destination);
            AABB marker = new AABB(
                    destinationPoint.x - 0.38D, destinationPoint.y + 0.03D, destinationPoint.z - 0.38D,
                    destinationPoint.x + 0.38D, destinationPoint.y + 0.16D, destinationPoint.z + 0.38D
            );
            LevelRenderer.renderLineBox(poses, lines, marker, 0.98F, 0.82F, 0.32F, 1.0F);
        }
        if (routeVisible && rejected != null)
            LevelRenderer.renderLineBox(poses, lines, new AABB(rejected).inflate(.03), 1f, .15f, .15f, 1f);
        dev.stonebanner.client.control.ContextFeedbackController.pulse(now).ifPresent(pulse -> {
            double size = 0.22D + (1.0f - pulse.opacity()) * 0.43D;
            Vec3 point = pulse.location();
            var ring = new AABB(point.x - size, point.y - 0.03D, point.z - size,
                    point.x + size, point.y + 0.10D, point.z + size);
            float r = pulse.kind() == dev.stonebanner.client.control.ContextFeedbackController.Kind.DENIED ? 1.0f : 0.98f;
            float g = pulse.kind() == dev.stonebanner.client.control.ContextFeedbackController.Kind.DENIED ? 0.21f : 0.82f;
            LevelRenderer.renderLineBox(poses, lines, ring, r, g, 0.30f, pulse.opacity());
        });
        if (markersVisible && hoveredEntity != null) {
            LevelRenderer.renderLineBox(
                    poses,
                    lines,
                    hoveredEntity.getBoundingBox().inflate(0.08D),
                    0.35F,
                    0.90F,
                    0.96F,
                    1.0F
            );
        }
        if (markersVisible && hoveredLocation != null && hoveredEntity == null) {
            LevelRenderer.renderLineBox(
                    poses,
                    lines,
                    AABB.ofSize(hoveredLocation, 0.16D, 0.16D, 0.16D),
                    0.98F,
                    0.84F,
                    0.32F,
                    1.0F
            );
        }
        if (markersVisible && selectedEntity != null && selectedEntity != hoveredEntity) {
            LevelRenderer.renderLineBox(
                    poses,
                    lines,
                    selectedEntity.getBoundingBox().inflate(0.12D),
                    0.98F,
                    0.74F,
                    0.20F,
                    1.0F
            );
        }
        // Build the target lookup only when marker rendering is enabled.
        if (markersVisible) {
        var selectedMembers = CitizenSelectionController.selectedAll();
        java.util.Map<java.util.UUID, Entity> loadedTargets = new java.util.HashMap<>();
        if (!selectedMembers.isEmpty() && minecraft.level != null) {
            for (var entity : minecraft.level.entitiesForRendering())
                if (entity.isAlive()) loadedTargets.put(entity.getUUID(), entity);
        }
        for (var selectedMember : selectedMembers) {
            LevelRenderer.renderLineBox(
                    poses,
                    lines,
                    selectedMember.getBoundingBox().inflate(0.14D),
                    0.42F,
                    0.95F,
                    0.35F,
                    1.0F
            );
            renderCitizenOrders(minecraft, poses, lines, selectedMember, loadedTargets);
        }
            renderExcavationPlans(poses, lines);
        }
        renderDesignationPreview(poses, lines);
        poses.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawRouteSegment(PoseStack poses, VertexConsumer lines,
                                         Vec3 start, Vec3 end, float opacity) {
        if (start.distanceToSqr(end) < 0.0001D) return;
        var pose = poses.last();
        lines.vertex(pose.pose(), (float) start.x, (float) start.y, (float) start.z)
                .color(0.96F, 0.77F, 0.34F, opacity)
                .normal(pose.normal(), 0, 1, 0).endVertex();
        lines.vertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                .color(0.96F, 0.77F, 0.34F, opacity)
                .normal(pose.normal(), 0, 1, 0).endVertex();
    }

    /** All markers are visual-only server snapshots and never drive a new command. */
    private static void renderCitizenOrders(Minecraft mc, PoseStack poses, VertexConsumer lines,
                                            dev.stonebanner.entity.HumanNpcEntity citizen,
                                            java.util.Map<java.util.UUID, Entity> loadedTargets) {
        if (mc.level == null) return;
        for (var preview : dev.stonebanner.command.CitizenOrderQueue.decodePreview(citizen.hudOrderPreview())) {
            var entry = preview.entry();
            Vec3 point = null;
            if (entry.block() != null) {
                if (!mc.level.hasChunkAt(entry.block())) continue;
                point = Vec3.atCenterOf(entry.block());
            } else {
                Entity target = loadedTargets.get(entry.target());
                if (target != null) point = target.getBoundingBox().getCenter();
            }
            if (point == null) continue;
            float r = preview.active() ? 1.0F : 0.32F;
            float g = preview.active() ? 0.83F : 0.76F;
            float b = preview.active() ? 0.18F : 0.99F;
            double radius = preview.active() ? 0.48D : 0.28D;
            var marker = new AABB(point.x - radius, point.y - 0.06D, point.z - radius,
                    point.x + radius, point.y + 0.12D, point.z + radius);
            LevelRenderer.renderLineBox(poses, lines, marker, r, g, b, preview.active() ? 1.0F : 0.68F);
        }
    }

    private static void renderExcavationPlans(PoseStack poses, VertexConsumer lines) {
        for (var plan : ExcavationOverlayState.plans()) {
            BlockPos min = plan.min();
            BlockPos max = plan.max();
            AABB wholePlan = new AABB(
                    min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D
            ).inflate(0.006D);

            float red = plan.hazardPaused() ? 1.0F : plan.modeCode() == 0 ? 0.86F : 0.68F;
            float green = plan.hazardPaused() ? 0.22F : plan.modeCode() == 0 ? 0.52F : 0.48F;
            float blue = plan.hazardPaused() ? 0.18F : plan.modeCode() == 0 ? 0.22F : 0.92F;
            LevelRenderer.renderLineBox(poses, lines, wholePlan, red, green, blue, 0.38F);

            AABB activeFront = currentSliceBox(plan.modeCode(), plan.currentSlice(), min, max);
            if (activeFront != null) {
                LevelRenderer.renderLineBox(
                        poses,
                        lines,
                        activeFront.inflate(0.014D),
                        red,
                        green,
                        blue,
                        1.0F
                );
            }
        }
    }

    private static AABB currentSliceBox(int modeCode, int currentSlice, BlockPos min, BlockPos max) {
        return switch (modeCode) {
            case 0 -> currentSlice < min.getY() || currentSlice > max.getY()
                    ? null
                    : new AABB(
                            min.getX(), currentSlice, min.getZ(),
                            max.getX() + 1.0D, currentSlice + 1.0D, max.getZ() + 1.0D
                    );
            case 1 -> currentSlice < min.getX() || currentSlice > max.getX()
                    ? null
                    : new AABB(
                            currentSlice, min.getY(), min.getZ(),
                            currentSlice + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D
                    );
            case 2 -> currentSlice < min.getZ() || currentSlice > max.getZ()
                    ? null
                    : new AABB(
                            min.getX(), min.getY(), currentSlice,
                            max.getX() + 1.0D, max.getY() + 1.0D, currentSlice + 1.0D
                    );
            default -> null;
        };
    }

    private static void renderDesignationPreview(PoseStack poses, VertexConsumer lines) {
        BlockPos first = DesignationController.selectionStart().orElse(null);
        BlockPos second = DesignationController.selectionEnd().orElse(null);
        DesignationType type = DesignationController.activeType().orElse(null);
        if (first == null || second == null || type == null) {
            return;
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX()) + 1;
        int maxY = Math.max(first.getY(), second.getY()) + 1;
        int maxZ = Math.max(first.getZ(), second.getZ()) + 1;
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.01D);

        float red = 1.0F;
        float green = 0.35F;
        float blue = 0.35F;
        if (!DesignationController.previewAllowed()) {
            red = 1.0F;
            green = 0.25F;
            blue = 0.25F;
        } else {
            switch (type) {
                case CHOP -> {
                    red = 0.42F;
                    green = 0.90F;
                    blue = 0.35F;
                }
                case MINE -> {
                    red = 0.90F;
                    green = 0.70F;
                    blue = 0.30F;
                }
                case EXCAVATE -> {
                    red = 0.86F;
                    green = 0.52F;
                    blue = 0.22F;
                }
                case TUNNEL -> {
                    red = 0.68F;
                    green = 0.48F;
                    blue = 0.92F;
                }
                case CLEAR -> {
                    red = 0.45F;
                    green = 0.85F;
                    blue = 0.75F;
                }
                case CANCEL -> {
                    red = 1.0F;
                    green = 0.35F;
                    blue = 0.35F;
                }
            }
        }
        LevelRenderer.renderLineBox(poses, lines, box, red, green, blue, 1.0F);
    }
}
