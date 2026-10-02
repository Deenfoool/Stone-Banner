package dev.stonebanner.client.control;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
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
    private PathPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ClientConfig.SHOW_PATH_PREVIEW.get()) {
            return;
        }

        List<BlockPos> path = PlayerCommandController.pathSnapshot();
        BlockPos destination = PlayerCommandController.destination().orElse(null);
        Entity hoveredEntity = WorldCursor.hoveredEntity().orElse(null);
        Vec3 hoveredLocation = WorldCursor.hoveredLocation().orElse(null);
        Entity selectedEntity = PlayerCommandController.selectedEntity().orElse(null);
        if (path.isEmpty() && destination == null && hoveredEntity == null
                && selectedEntity == null && hoveredLocation == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPos point : path) {
            Vec3 waypoint = minecraft.level == null
                    ? Vec3.atBottomCenterOf(point)
                    : BlockPathfinder.waypoint(minecraft.level, point);
            AABB marker = new AABB(
                    waypoint.x - 0.20D, waypoint.y + 0.04D, waypoint.z - 0.20D,
                    waypoint.x + 0.20D, waypoint.y + 0.10D, waypoint.z + 0.20D
            );
            LevelRenderer.renderLineBox(poses, lines, marker, 0.90F, 0.68F, 0.22F, 0.85F);
        }
        if (destination != null) {
            Vec3 destinationPoint = minecraft.level == null
                    ? Vec3.atBottomCenterOf(destination)
                    : BlockPathfinder.waypoint(minecraft.level, destination);
            AABB marker = new AABB(
                    destinationPoint.x - 0.38D, destinationPoint.y + 0.03D, destinationPoint.z - 0.38D,
                    destinationPoint.x + 0.38D, destinationPoint.y + 0.16D, destinationPoint.z + 0.38D
            );
            LevelRenderer.renderLineBox(poses, lines, marker, 0.98F, 0.82F, 0.32F, 1.0F);
        }
        if (hoveredEntity != null) {
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
        if (hoveredLocation != null && hoveredEntity == null) {
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
        if (selectedEntity != null && selectedEntity != hoveredEntity) {
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
        poses.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
