package dev.stonebanner.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact language picker exposed directly from the Minecraft title screen. */
public final class StoneBannerLanguageScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private static final List<LanguageChoice> LANGUAGES = List.of(
            new LanguageChoice("ru_ru", "Русский"),
            new LanguageChoice("en_us", "English"),
            new LanguageChoice("de_de", "Deutsch"),
            new LanguageChoice("zh_cn", "简体中文")
    );

    private final Screen parent;

    public StoneBannerLanguageScreen(Screen parent) {
        super(Component.translatable("screen.stonebanner.language.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = Math.max(52, height / 4);

        for (LanguageChoice choice : LANGUAGES) {
            addRenderableWidget(Button.builder(
                    languageLabel(choice),
                    button -> applyLanguage(choice.code())
            ).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }

        y += 8;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                button -> onClose()
        ).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);

        String currentName = LANGUAGES.stream()
                .filter(choice -> choice.code().equals(minecraft.getLanguageManager().getSelected()))
                .map(LanguageChoice::displayName)
                .findFirst()
                .orElse(minecraft.getLanguageManager().getSelected());

        graphics.drawCenteredString(
                font,
                Component.translatable("screen.stonebanner.language.current", currentName),
                width / 2,
                36,
                0xFFB8B8B8
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private Component languageLabel(LanguageChoice choice) {
        boolean selected = minecraft != null
                && choice.code().equals(minecraft.getLanguageManager().getSelected());
        return Component.literal(selected ? "> " + choice.displayName() + " <" : choice.displayName());
    }

    private void applyLanguage(String code) {
        Minecraft client = Minecraft.getInstance();
        if (code.equals(client.getLanguageManager().getSelected())) {
            client.setScreen(parent);
            return;
        }

        client.getLanguageManager().setSelected(code);
        client.options.languageCode = code;
        client.options.save();
        client.reloadResourcePacks();

        // Keep the originating game/menu context; returning to Title would disconnect
        // a player who only wanted to change language in the settings screen.
        client.setScreen(parent instanceof net.minecraft.client.gui.screens.TitleScreen
                ? new net.minecraft.client.gui.screens.TitleScreen() : parent);
    }

    private record LanguageChoice(String code, String displayName) {
    }
}
