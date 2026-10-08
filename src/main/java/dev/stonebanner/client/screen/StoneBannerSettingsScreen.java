package dev.stonebanner.client.screen;

import dev.stonebanner.client.ClientRuntime;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.ControlMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Live preferences for the two Hero profiles and their shared RTS presentation.
 * Key remapping remains delegated to vanilla's persistent Options / Key Binds UI.
 */
public final class StoneBannerSettingsScreen extends Screen {
    private enum Page { CONTROLS, CAMERA, FEEDBACK }
    private final Screen parent;
    private Page page = Page.CONTROLS;

    public StoneBannerSettingsScreen(Screen parent) {
        super(Component.translatable("settings.stonebanner.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int gap = 6;
        int columnWidth = Math.max(72, (width - 24) / 2);
        int startX = Math.max(6, (width - (columnWidth * 2 + gap)) / 2);
        int rowStep = Math.max(21, Math.min(28, (height - 102) / 4));
        int startY = Math.max(57, Math.min(69, height / 4));
        int tabWidth = Math.max(70, Math.min(112, (width - 24) / 3));
        int tabStart = Math.max(6, (width - (tabWidth * 3 + gap * 2)) / 2);
        for (Page choice : Page.values()) {
            int x = tabStart + choice.ordinal() * (tabWidth + gap);
            addRenderableWidget(Button.builder(Component.translatable(
                    "settings.stonebanner.tab." + choice.name().toLowerCase(java.util.Locale.ROOT)),
                    button -> { page = choice; rebuildWidgets(); })
                    .bounds(x, 29, tabWidth, 20).build());
        }

        switch (page) {
            case CONTROLS -> {
                option(startX, startY, columnWidth, rowStep, 0,
                        "settings.stonebanner.profile", () -> Component.translatable(
                                ClientConfig.controlMode() == ControlMode.ACTION
                                        ? "settings.stonebanner.profile.wasd" : "settings.stonebanner.profile.mouse"),
                        () -> ClientRuntime.setControlMode(minecraft, ClientConfig.controlMode().next()));
                toggle(startX, startY, columnWidth, rowStep, 1,
                        "settings.stonebanner.auto_approach", ClientConfig.AUTO_APPROACH);
                toggle(startX, startY, columnWidth, rowStep, 2,
                        "settings.stonebanner.safe_path", ClientConfig.SAFE_PATH);
                toggle(startX, startY, columnWidth, rowStep, 3,
                        "settings.stonebanner.double_run", ClientConfig.DOUBLE_CLICK_RUN);
                toggle(startX, startY, columnWidth, rowStep, 4,
                        "settings.stonebanner.hold_tab", ClientConfig.HOLD_TAB);
                cycleInt(startX, startY, columnWidth, rowStep, 5,
                        "settings.stonebanner.hold_duration", ClientConfig.ORDERS_HOLD_MS,
                        new int[]{180, 230, 300, 400, 600});
                cycleInt(startX, startY, columnWidth, rowStep, 6,
                        "settings.stonebanner.double_duration", ClientConfig.DOUBLE_CLICK_MS,
                        new int[]{200, 250, 300, 400, 500});
                cycleInt(startX, startY, columnWidth, rowStep, 7,
                        "settings.stonebanner.path_interval", ClientConfig.HELD_PATH_INTERVAL,
                        new int[]{5, 8, 10, 15, 20});
            }
            case CAMERA -> {
                cycleDouble(startX, startY, columnWidth, rowStep, 0,
                        "settings.stonebanner.camera_sens", ClientConfig.CAMERA_ROTATION_SENSITIVITY,
                        new double[]{0.3, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0});
                toggle(startX, startY, columnWidth, rowStep, 1,
                        "settings.stonebanner.invert", ClientConfig.CAMERA_INVERT_VERTICAL);
                cycleDouble(startX, startY, columnWidth, rowStep, 2,
                        "settings.stonebanner.zoom", ClientConfig.CAMERA_ZOOM_SENSITIVITY,
                        new double[]{0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0});
                toggle(startX, startY, columnWidth, rowStep, 3,
                        "settings.stonebanner.edge_pan", ClientConfig.CAMERA_EDGE_PAN);
                cycleDouble(startX, startY, columnWidth, rowStep, 4,
                        "settings.stonebanner.pan_sens", ClientConfig.CAMERA_PAN_SENSITIVITY,
                        new double[]{0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0});
                cycleDouble(startX, startY, columnWidth, rowStep, 5,
                        "settings.stonebanner.distance", ClientConfig.CAMERA_DISTANCE,
                        new double[]{4, 6, 8, 10, 12, 16, 20});
                cycleDouble(startX, startY, columnWidth, rowStep, 6,
                        "settings.stonebanner.smoothing", ClientConfig.CAMERA_SMOOTHING,
                        new double[]{0, 0.15, 0.35, 0.5, 0.7, 0.85});
                cycleDouble(startX, startY, columnWidth, rowStep, 7,
                        "settings.stonebanner.height", ClientConfig.CAMERA_HEIGHT,
                        new double[]{0, 1, 2, 3, 4, 6, 8});
            }
            case FEEDBACK -> {
                toggle(startX, startY, columnWidth, rowStep, 0,
                        "settings.stonebanner.hints", ClientConfig.SHOW_CONTEXT_HINTS);
                cycleInt(startX, startY, columnWidth, rowStep, 1,
                        "settings.stonebanner.hint_delay", ClientConfig.CONTEXT_HINT_DELAY_MS,
                        new int[]{250, 350, 480, 650, 900, 1200});
                toggle(startX, startY, columnWidth, rowStep, 2,
                        "settings.stonebanner.route", ClientConfig.SHOW_PATH_PREVIEW);
                toggle(startX, startY, columnWidth, rowStep, 3,
                        "settings.stonebanner.markers", ClientConfig.SHOW_ORDER_MARKERS);
                toggle(startX, startY, columnWidth, rowStep, 4,
                        "settings.stonebanner.feedback", ClientConfig.SHOW_COMMAND_FEEDBACK);
                toggle(startX, startY, columnWidth, rowStep, 5,
                        "settings.stonebanner.third_person", ClientConfig.ENFORCE_THIRD_PERSON);
            }
        }

        int footerY = Math.max(53, height - 25);
        int footerWidth = Math.max(67, (width - 24) / 3);
        int footerX = Math.max(6, (width - (footerWidth * 3 + gap * 2)) / 2);
        addRenderableWidget(Button.builder(Component.translatable("settings.stonebanner.keys"),
                button -> minecraft.setScreen(new ControlBindingsScreen(this)))
                .bounds(footerX, footerY, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.stonebanner.language"),
                button -> minecraft.setScreen(new StoneBannerLanguageScreen(this)))
                .bounds(footerX + footerWidth + gap, footerY, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(footerX + (footerWidth + gap) * 2, footerY, footerWidth, 20).build());
    }

    private void option(int x, int y, int w, int step, int index, String key,
                        java.util.function.Supplier<Component> value, Runnable apply) {
        int px = x + (index % 2) * (w + 6);
        int py = y + (index / 2) * step;
        addRenderableWidget(Button.builder(label(key, value.get()), button -> {
            apply.run();
            button.setMessage(label(key, value.get()));
        }).bounds(px, py, w, 20).build());
    }

    private static Component label(String key, Component value) {
        return Component.translatable("settings.stonebanner.option",
                Component.translatable(key), value);
    }

    private void toggle(int x, int y, int w, int step, int index, String key,
                        ForgeConfigSpec.BooleanValue config) {
        option(x, y, w, step, index, key,
                () -> Component.translatable(config.get() ? "options.on" : "options.off"),
                () -> config.set(!config.get()));
    }

    private void cycleInt(int x, int y, int w, int step, int index, String key,
                          ForgeConfigSpec.IntValue config, int[] values) {
        option(x, y, w, step, index, key, () -> Component.literal(Integer.toString(config.get())),
                () -> config.set(values[nextIndex(values, config.get())]));
    }

    private void cycleDouble(int x, int y, int w, int step, int index, String key,
                             ForgeConfigSpec.DoubleValue config, double[] values) {
        option(x, y, w, step, index, key,
                () -> Component.literal(String.format(java.util.Locale.ROOT, "%.2f", config.get())),
                () -> config.set(values[nextIndex(values, config.get())]));
    }

    /** Advance to next preset without replacing legacy custom TOML values until clicked. */
    static int nextIndex(int[] values, int current) {
        for (int i = 0; i < values.length; i++) if (values[i] > current) return i;
        return 0;
    }

    static int nextIndex(double[] values, double current) {
        for (int i = 0; i < values.length; i++) if (values[i] > current + 0.00001) return i;
        return 0;
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFF4E4C7);
        graphics.drawCenteredString(font, Component.translatable("settings.stonebanner.note"),
                width / 2, Math.max(51, Math.min(56, height / 4 - 9)), 0xFFA8B6BE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
