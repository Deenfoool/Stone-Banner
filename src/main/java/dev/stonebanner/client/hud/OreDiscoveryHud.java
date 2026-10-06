package dev.stonebanner.client.hud;

import dev.stonebanner.client.ClientKeyMappings;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.OreDiscoveryState;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.client.screen.OreDiscoveriesScreen;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.OreDiscoverySnapshotPacket.Finding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** Compact pixel panel with identical draw and hit-test geometry. */
public final class OreDiscoveryHud {
    private static final String[] ACTIONS = {"approve", "show", "focus", "hide"};
    private OreDiscoveryHud() {}
    public static Component oreName(Finding finding) {
        return BuiltInRegistries.BLOCK.get(finding.block()).getName();
    }
    public static void render(GuiGraphics g, Minecraft mc, int width, int height) {
        if (RpgCameraController.hasFocus()) {
            Component hint = Component.translatable("ore.stonebanner.focus_hint",
                    ClientKeyMappings.ORE_JOURNAL.getTranslatedKeyMessage());
            int hintWidth = mc.font.width(hint) + 12;
            int hx = Math.max(4, (width - hintWidth) / 2);
            g.fill(hx, 44, Math.min(width - 4, hx + hintWidth), 60, 0xDD171B1E);
            g.drawString(mc.font, hint, hx + 6, 48, 0xFFE7C46A, false);
        }
        var finding = OreDiscoveryState.notification().orElse(null);
        if (finding == null) return;
        int w = Math.min(300, width - 16), x = width - w - 8, y = 86;
        g.fill(x, y, x + w, y + 114, 0xDD171B1E);
        g.renderOutline(x, y, w, 114, 0xFFAA864B);
        g.drawString(mc.font, Component.translatable("ore.stonebanner.discovered"), x + 7, y + 7, 0xFFFFCD70, false);
        g.drawString(mc.font, oreName(finding), x + 7, y + 21, 0xFFFFFFFF, false);
        var pos = finding.anchor();
        g.drawString(mc.font, Component.translatable("ore.stonebanner.details", finding.positions().size(),
                pos.getX(), pos.getY(), pos.getZ()), x + 7, y + 34, 0xFFD8D2C8, false);
        g.drawString(mc.font, Component.translatable(finding.approved() ? "ore.stonebanner.allowed_zone" : "ore.stonebanner.waiting"),
                x + 7, y + 47, 0xFFD8D2C8, false);
        g.drawString(mc.font, Component.translatable("ore.stonebanner.open_hint",
                ClientKeyMappings.ORE_JOURNAL.getTranslatedKeyMessage()),
                x + 7, y + 101, 0xFFD8D2C8, false);
        int cell = (w - 16) / 2;
        for (int i = 0; i < 4; i++) {
            int bx = x + 6 + (i % 2) * (cell + 4), by = y + 60 + (i / 2) * 18;
            g.fill(bx, by, bx + cell, by + 16, 0xFF343B3F);
            g.drawCenteredString(mc.font, Component.translatable("ore.stonebanner." + ACTIONS[i]), bx + cell / 2, by + 4, 0xFFE7C46A);
        }
    }
    public static boolean click(double mx, double my, int width) {
        var finding = OreDiscoveryState.notification().orElse(null);
        if (finding == null) return false;
        int w = Math.min(300, width - 16), x = width - w - 8, y = 86, cell = (w - 16) / 2;
        if (mx < x || mx >= x + w || my < y || my >= y + 114) return false;
        for (int i = 0; i < 4; i++) {
            int bx = x + 6 + (i % 2) * (cell + 4), by = y + 60 + (i / 2) * 18;
            if (mx >= bx && mx < bx + cell && my >= by && my < by + 16) act(finding, i);
        }
        return true;
    }
    public static void act(Finding finding, int action) {
        switch (action) {
            case 0 -> StoneBannerNetwork.sendOreAction(finding.id(), true);
            case 1 -> {
                OreDiscoveryState.highlight(finding.id());
                var mc = Minecraft.getInstance();
                if (mc.screen instanceof OreDiscoveriesScreen journal) journal.onClose();
            }
            case 2 -> {
                var mc = Minecraft.getInstance();
                if (mc.level != null && mc.level.hasChunkAt(finding.anchor())) {
                    PlayerCommandController.stop();
                    RpgCameraController.focusOn(finding.anchor());
                    if (mc.screen instanceof OreDiscoveriesScreen) mc.setScreen(null);
                } else if (mc.player != null) mc.player.displayClientMessage(Component.translatable("ore.stonebanner.unloaded"), true);
            }
            case 3 -> {
                OreDiscoveryState.dismiss(finding.id());
                StoneBannerNetwork.sendOreAction(finding.id(), false);
            }
            default -> throw new IllegalArgumentException("Unknown ore action");
        }
    }
}
