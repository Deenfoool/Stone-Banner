package dev.stonebanner.client.screen;

import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.OreDiscoveryState;
import dev.stonebanner.client.hud.OreDiscoveryHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A non-pausing journal preserves dismissed discoveries and exposes the same four actions in all modes. */
public final class OreDiscoveriesScreen extends Screen {
    private int index;
    private final Screen parent;
    public OreDiscoveriesScreen(Screen parent) { super(Component.translatable("ore.stonebanner.journal")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        int x = width / 2 - 145, y = height / 2;
        String[] actions = {"approve", "show", "focus", "hide"};
        for (int i = 0; i < 4; i++) {
            final int action = i;
            addRenderableWidget(Button.builder(Component.translatable("ore.stonebanner." + actions[i]), button -> {
                var findings = OreDiscoveryState.findings();
                if (!findings.isEmpty()) OreDiscoveryHud.act(findings.get(Math.min(index, findings.size() - 1)), action);
            }).bounds(x + (i % 2) * 148, y + (i / 2) * 24, 142, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> index = Math.max(0, index - 1)).bounds(x, y + 54, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> index = Math.min(Math.max(0, OreDiscoveryState.findings().size() - 1), index + 1)).bounds(x + 250, y + 54, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ore.stonebanner.return"), b -> RpgCameraController.clearFocus()).bounds(x + 46, y + 54, 198, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 46, y + 80, 198, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 75, 0xFFE7C46A);
        var findings = OreDiscoveryState.findings();
        if (findings.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("ore.stonebanner.empty"), width / 2, height / 2 - 40, 0xFFFFFFFF);
        } else {
            index = Math.min(index, findings.size() - 1);
            var finding = findings.get(index); var pos = finding.anchor();
            g.drawCenteredString(font, OreDiscoveryHud.oreName(finding), width / 2, height / 2 - 55, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("ore.stonebanner.details", finding.positions().size(), pos.getX(), pos.getY(), pos.getZ()), width / 2, height / 2 - 40, 0xFFD8D2C8);
            g.drawCenteredString(font, Component.translatable(finding.approved() ? "ore.stonebanner.allowed_zone" : "ore.stonebanner.waiting"), width / 2, height / 2 - 25, 0xFFD8D2C8);
            g.drawCenteredString(font, (index + 1) + " / " + findings.size(), width / 2, height / 2 - 12, 0xFFD8D2C8);
        }
        super.render(g, mx, my, partial);
    }
}
