package dev.stonebanner.client.hud;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.ClientKeyMappings;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.ExcavationOverlayState;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.client.control.HeroInputController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/** Read-only client diagnostics; never requests server state or scans world blocks. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class DebugOverlay {
    private static boolean enabled;
    private static List<Component> lines = List.of();
    private static int refreshTicks;

    private DebugOverlay() {
    }

    public static void toggle() {
        enabled = !enabled;
        lines = List.of();
        refreshTicks = 0;
        StoneAndBanner.LOGGER.debug("Client diagnostics {}", enabled ? "enabled" : "disabled");
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        lines = List.of();
        refreshTicks = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            lines = List.of();
            refreshTicks = 0;
            return;
        }
        if (refreshTicks-- > 0) return;
        refreshTicks = 4;
        var camera = mc.gameRenderer.getMainCamera();
        var selected = CitizenSelectionController.selectedAll();
        Component npc = selected.isEmpty() ? Component.literal("—")
                : Component.translatable("debug.stonebanner.npc", selected.get(0).getId(),
                        selected.get(0).brainState().name(), Component.translatable(selected.get(0).hudWorkBlockReason().key()),
                        selected.get(0).hudQueuedMoves());
        var pos = camera.getPosition();
        lines = List.of(
                Component.translatable("debug.stonebanner.title", ClientKeyMappings.DEBUG_OVERLAY.getTranslatedKeyMessage()),
                Component.translatable("debug.stonebanner.mode", ClientConfig.controlMode().displayName(),
                        Component.translatable(HeroInputController.commandMode() ? "debug.stonebanner.orders" : "debug.stonebanner.hero")),
                Component.translatable("debug.stonebanner.camera", String.format(Locale.ROOT, "%.1f / %.1f / %.1f", pos.x, pos.y, pos.z)),
                Component.translatable("debug.stonebanner.focus", Component.translatable(RpgCameraController.followingEntity()
                        ? "debug.stonebanner.focus_entity" : RpgCameraController.hasFocus()
                        ? "debug.stonebanner.focus_point" : "debug.stonebanner.focus_free")),
                Component.translatable("debug.stonebanner.view", String.format(Locale.ROOT, "%.1f / %.1f", camera.getYRot(), camera.getXRot()),
                        Component.translatable(RpgCameraController.viewObstructed() ? "debug.stonebanner.blocked" : "debug.stonebanner.clear")),
                Component.translatable("debug.stonebanner.route", PlayerCommandController.status().name(),
                        PlayerCommandController.remainingPathNodes(), PlayerCommandController.destination().map(DebugOverlay::coordinates).orElse("—"),
                        PlayerCommandController.queuedMoveCount()),
                Component.translatable("debug.stonebanner.selection", selected.size(), npc),
                Component.translatable("debug.stonebanner.world", mc.level.dimension().location().toString(),
                        ExcavationOverlayState.plans().size()),
                Component.translatable("debug.stonebanner.clock", mc.getSingleplayerServer() == null
                        ? Component.translatable("debug.stonebanner.remote") : Component.literal(GameSpeedController.speed().name()))
        );
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // TacticalControlScreen renders diagnostics itself, after its own HUD.
        if (mc.screen == null) render(event.getGuiGraphics(), mc,
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (!enabled || mc.options.hideGui || mc.player == null || mc.level == null || lines.isEmpty()) return;
        DebugPanelLayout.fit(width, height, lines.size()).ifPresent(layout -> {
            graphics.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), 0xCF10151A);
            for (int row = 0; row < layout.rows(); row++) {
                String text = mc.font.plainSubstrByWidth(lines.get(row).getString(), layout.width() - 8);
                graphics.drawString(mc.font, text, layout.x() + 4, layout.y() + 4 + row * 10,
                        row == 0 ? 0xFF69DDE7 : 0xFFD8D2C8);
            }
        });
    }
}
