package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.camera.RpgCameraController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class OreDiscoveryOverlay {
    private OreDiscoveryOverlay() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        OreDiscoveryState.clear(); RpgCameraController.clearFocus();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var positions = OreDiscoveryState.highlighted();
        if (positions.isEmpty()) return;
        var camera = event.getCamera().getPosition(); PoseStack poses = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        for (var pos : positions) {
            if (mc.level.hasChunkAt(pos) && mc.level.getBlockState(pos).is(net.minecraftforge.common.Tags.Blocks.ORES))
                LevelRenderer.renderLineBox(poses, lines, new AABB(pos).inflate(.003), 1f, .78f, .2f, 1f);
        }
        poses.popPose(); buffers.endBatch(RenderType.lines());
    }
}
