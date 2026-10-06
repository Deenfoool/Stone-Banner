package dev.stonebanner.client.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.stonebanner.StoneAndBanner;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Fail closed if the entity itself is engulfed in geometry, including when the GUI is hidden with F1. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class CameraObstructionOverlay {
    private CameraObstructionOverlay() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !RpgCameraController.viewObstructed()) return;
        // Clear only world pixels; inventory, controls and menus remain available to leave the obstruction.
        RenderSystem.clearColor(.06f, .06f, .08f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.clearColor(0, 0, 0, 0);
    }
}
