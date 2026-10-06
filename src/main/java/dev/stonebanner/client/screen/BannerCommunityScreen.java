package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.BannerCommunitySnapshotPacket;
import dev.stonebanner.settlement.BannerCommunityService.Action;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Vanilla banner remains decorative until an explicit named and confirmed founding action. */
public final class BannerCommunityScreen extends Screen {
    private final BannerCommunitySnapshotPacket state;
    private final Screen parent;
    private boolean founding;
    private EditBox name;
    public BannerCommunityScreen(BannerCommunitySnapshotPacket state, Screen parent) {
        super(Component.translatable("community.stonebanner.menu")); this.state = state; this.parent = parent;
    }
    public static void open(BannerCommunitySnapshotPacket state) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(state.dimension())) return;
        Screen parent = mc.screen instanceof BannerCommunityScreen menu ? menu.parent : mc.screen;
        PlayerCommandController.stop(); mc.setScreen(new BannerCommunityScreen(state, parent));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override protected void init() {
        int x = width / 2 - 150, y = Math.max(50, height / 2 - 70);
        boolean ownBanner = state.owner() && state.banner().equals(state.target()) && state.bannerActive();
        if (founding) {
            String previousName = name == null
                    ? Component.translatable("community.stonebanner.default_name").getString() : name.getValue();
            name = new EditBox(font, x + 10, y + 52, 280, 20, Component.translatable("community.stonebanner.name"));
            name.setMaxLength(32); name.setValue(previousName);
            addRenderableWidget(name); setInitialFocus(name);
            var confirm = addRenderableWidget(Button.builder(Component.translatable("community.stonebanner.confirm_found"), b ->
                    send(Action.CREATE, name.getValue())).bounds(x, y + 82, 300, 20).build());
            confirm.active = !SettlementData.validName(name.getValue()).isEmpty();
            name.setResponder(value -> confirm.active = !SettlementData.validName(value).isEmpty());
        } else if (!state.hasCommunity()) {
            addRenderableWidget(Button.builder(Component.translatable("community.stonebanner.found"), b -> {
                founding = true; rebuildWidgets();
            }).bounds(x, y + 82, 300, 20).build());
        } else if (state.owner()) {
            if (!ownBanner) {
                var move = addRenderableWidget(Button.builder(Component.translatable("community.stonebanner.move"), b -> confirm(Action.MOVE,
                        "community.stonebanner.move_confirm")).bounds(x, y + 112, 300, 20).build());
                move.active = Math.abs((long)(state.target().getX() >> 4) - state.centerX()) <= 1
                        && Math.abs((long)(state.target().getZ() >> 4) - state.centerZ()) <= 1;
            } else {
                if (!state.settlement()) {
                    var upgrade = addRenderableWidget(Button.builder(Component.translatable("community.stonebanner.upgrade"), b -> confirm(Action.PROMOTE,
                            "community.stonebanner.upgrade_confirm")).bounds(x, y + 112, 300, 20).build());
                    upgrade.active = state.residents() >= 1 && state.beds() >= state.assigned() + 1
                            && state.food() >= 4 * (state.assigned() + 1) && state.stores() >= 1;
                }
                var resident = addRenderableWidget(Button.builder(Component.translatable("community.stonebanner.accept_settler"), b -> {
                    int id = CitizenSelectionController.selected().map(npc -> npc.getId()).orElse(-1);
                    StoneBannerNetwork.sendBannerAction(state.target(), Action.ADD_RESIDENT, "", id);
                }).bounds(x, y + 136, 300, 20).build());
                resident.active = CitizenSelectionController.selected().isPresent();
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, y + 162, 300, 20).build());
    }
    private void send(Action action, String name) { StoneBannerNetwork.sendBannerAction(state.target(), action, name, -1); }
    private void confirm(Action action, String key) {
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(this); if (accepted) send(action, "");
        }, Component.translatable("community.stonebanner.menu"), Component.translatable(key)));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int x = width / 2 - 150, y = Math.max(50, height / 2 - 70);
        g.drawCenteredString(font, title, width / 2, y - 22, 0xFFE7C46A);
        if (founding) {
            g.drawCenteredString(font, Component.translatable("community.stonebanner.name"), width / 2, y + 36, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("community.stonebanner.area", state.centerX() - 1, state.centerZ() - 1,
                    state.centerX() + 1, state.centerZ() + 1), width / 2, y + 16, 0xFFD8D2C8);
        } else if (!state.hasCommunity()) {
            g.drawCenteredString(font, Component.translatable("community.stonebanner.decorative"), width / 2, y + 6, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("community.stonebanner.decorative_hint"), width / 2, y + 26, 0xFFD8D2C8);
        } else {
            g.drawCenteredString(font, Component.literal(state.name()).append(" — ").append(Component.translatable(
                    state.settlement() ? "community.stonebanner.settlement" : "community.stonebanner.camp")), width / 2, y, 0xFFFFFFFF);
            g.drawString(font, Component.translatable("community.stonebanner.area", state.centerX() - 1, state.centerZ() - 1,
                    state.centerX() + 1, state.centerZ() + 1), x, y + 18, 0xFFD8D2C8, false);
            g.drawString(font, Component.translatable("community.stonebanner.residents", state.residents(), state.assigned()), x, y + 34, 0xFFD8D2C8, false);
            g.drawString(font, Component.translatable("community.stonebanner.provisions", state.beds(), state.assigned() + 1,
                    state.food(), (state.assigned() + 1) * 4, state.stores()), x, y + 50, 0xFFD8D2C8, false);
            g.drawString(font, Component.translatable(!state.owner() ? "community.stonebanner.readonly" :
                    !state.bannerActive() ? "community.stonebanner.missing_banner" : "community.stonebanner.banner_position",
                    state.banner().getX(), state.banner().getY(), state.banner().getZ()), x, y + 66, 0xFFE7C46A, false);
            if (state.owner()) g.drawString(font, Component.translatable(
                    !state.banner().equals(state.target()) || !state.bannerActive()
                            ? "community.stonebanner.move_hint" : "community.stonebanner.settler_hint"), x, y + 86, 0xFFD8D2C8, false);
        }
        super.render(g, mx, my, partial);
    }
}
