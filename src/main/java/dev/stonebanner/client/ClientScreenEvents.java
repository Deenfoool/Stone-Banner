package dev.stonebanner.client;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.screen.StoneBannerLanguageScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import dev.stonebanner.client.screen.StoneBannerSettingsScreen;
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

    /** Opt-in acceptance tracing: no screen/input mutation and no output in ordinary runs. */
    @SubscribeEvent
    public static void traceAcceptanceScreen(ScreenEvent.Opening event) {
        if (Boolean.getBoolean("stonebanner.qaDiagnostics")) {
            com.mojang.logging.LogUtils.getLogger().info("P5 QA opening screen: {}",
                    event.getNewScreen() == null ? "<world>" : event.getNewScreen().getClass().getName());
            if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen disconnected) {
                com.mojang.logging.LogUtils.getLogger().warn("P5 QA disconnect reason: {}",
                        disconnected.getNarrationMessage().getString());
            }
        }
    }

    @SubscribeEvent
    public static void storageHint(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if(event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item
                && (item.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    || item.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock))
            event.getToolTip().add(Component.translatable("storage.stonebanner.ui.open_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var current = event.getScreen();
        if (current instanceof TitleScreen titleScreen) {
            event.addListener(Button.builder(
                    Component.translatable("menu.stonebanner.language"),
                    button -> Minecraft.getInstance().setScreen(new StoneBannerLanguageScreen(titleScreen))
            ).bounds(
                    Math.max(MARGIN, titleScreen.width - BUTTON_WIDTH - MARGIN),
                    MARGIN,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT
            ).build());
            event.addListener(Button.builder(
                    Component.translatable("settings.stonebanner.title"),
                    button -> Minecraft.getInstance().setScreen(new StoneBannerSettingsScreen(titleScreen))
            ).bounds(
                    Math.max(MARGIN, titleScreen.width - BUTTON_WIDTH - MARGIN),
                    MARGIN + BUTTON_HEIGHT + 3,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT
            ).build());
        } else if (current instanceof PauseScreen pauseScreen) {
            event.addListener(Button.builder(
                    Component.translatable("settings.stonebanner.title"),
                    button -> Minecraft.getInstance().setScreen(new StoneBannerSettingsScreen(pauseScreen))
            ).bounds(
                    Math.max(MARGIN, pauseScreen.width - BUTTON_WIDTH - MARGIN),
                    MARGIN,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT
            ).build());
        }
    }
}
