package dev.stonebanner.client;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.screen.StoneBannerLanguageScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Small client-only additions to vanilla screens.
 *
 * <p>The mod intentionally keeps Minecraft's title screen instead of replacing it. We only add the
 * Stone & Banner language entry so the rest of the vanilla menu stays compatible with Forge and
 * other mods.</p>
 */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenEvents {
    private static final int BUTTON_WIDTH = 112;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN = 6;

    private ClientScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen titleScreen)) {
            return;
        }

        event.addListener(Button.builder(
                Component.translatable("menu.stonebanner.language"),
                button -> Minecraft.getInstance().setScreen(new StoneBannerLanguageScreen(titleScreen))
        ).bounds(
                Math.max(MARGIN, titleScreen.width - BUTTON_WIDTH - MARGIN),
                MARGIN,
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        ).build());
    }
}
