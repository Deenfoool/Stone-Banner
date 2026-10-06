package dev.stonebanner.client.hud;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the shared Stone & Banner HUD in Action/Hybrid when no interactive screen is open. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class ClientHudEvents {
    private ClientHudEvents() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        OreDiscoveryHud.render(event.getGuiGraphics(), minecraft,
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        StoneBannerHudRenderer.render(
                event.getGuiGraphics(),
                minecraft,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight()
        );
    }
}
