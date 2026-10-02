package dev.stonebanner.client.hud;

import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-focused tactical HUD inspired by colony-management information design.
 * The world centre intentionally stays clear; panels only occupy the screen edges.
 */
public final class StoneBannerHudRenderer {
    private static final int PANEL = 0xD4111418;
    private static final int PANEL_SOFT = 0xC20C0F12;
    private static final int BORDER = 0xB86E6658;
    private static final int BORDER_ACTIVE = 0xE0B7793F;
    private static final int TEXT = 0xFFF0ECE3;
    private static final int MUTED = 0xFFAAA49A;
    private static final int ACCENT = 0xFFE0B66A;
    private static final int GOOD = 0xFF79C979;
    private static final int WARNING = 0xFFE2B85C;
    private static final int DANGER = 0xFFE76F6F;
    private static final int BAR_TRACK = 0xFF282C31;

    private StoneBannerHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.font == null || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        renderStrategicBar(graphics, minecraft.font, screenWidth);

        HumanNpcEntity selected = CitizenSelectionController.selected().orElse(null);
        if (selected != null) {
            renderCitizenCard(graphics, minecraft.font, selected, screenHeight);
            renderAlerts(graphics, minecraft.font, selected, screenWidth);
        }

        renderContextBar(graphics, minecraft.font, screenWidth, screenHeight, selected);
    }

    private static void renderStrategicBar(GuiGraphics graphics, Font font, int screenWidth) {
        int x = 8;
        int y = 8;
        int width = Math.max(240, screenWidth - 16);
        int height = 27;
        panel(graphics, x, y, width, height, false);

        MutableComponent title = Component.literal("STONE & BANNER").withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, title, x + 8, y + 9, ACCENT);

        int cursor = x + 104;
        cursor = metric(graphics, font, cursor, y + 5,
                Component.translatable("hud.stonebanner.top.settlement"),
                Component.translatable("hud.stonebanner.top.not_founded"), MUTED);

        if (screenWidth >= 720) {
            cursor = metric(graphics, font, cursor, y + 5,
                    Component.translatable("hud.stonebanner.top.population"), Component.literal("—"), TEXT);
            cursor = metric(graphics, font, cursor, y + 5,
                    Component.translatable("hud.stonebanner.top.food"), Component.literal("—"), TEXT);
            cursor = metric(graphics, font, cursor, y + 5,
                    Component.translatable("hud.stonebanner.top.treasury"), Component.literal("—"), TEXT);
            cursor = metric(graphics, font, cursor, y + 5,
                    Component.translatable("hud.stonebanner.top.prosperity"), Component.literal("—"), TEXT);
        }
        if (screenWidth >= 1040) {
            metric(graphics, font, cursor, y + 5,
                    Component.translatable("hud.stonebanner.top.materials"), Component.literal("—"), TEXT);
        }
    }

    private static int metric(GuiGraphics graphics, Font font, int x, int y,
                              Component label, Component value, int valueColor) {
        int labelWidth = font.width(label);
        int valueWidth = font.width(value);
        int width = labelWidth + valueWidth + 15;
        graphics.fill(x, y, x + width, y + 17, PANEL_SOFT);
        graphics.drawString(font, label, x + 5, y + 5, MUTED);
        graphics.drawString(font, value, x + 8 + labelWidth, y + 5, valueColor);
        return x + width + 4;
    }

    private static void renderCitizenCard(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenHeight) {
        int width = 210;
        int height = 151;
        int x = 8;
        int y = Math.max(44, screenHeight - height - 40);
        panel(graphics, x, y, width, height, true);

        graphics.drawString(font,
                npc.getDisplayName().copy().withStyle(ChatFormatting.BOLD),
                x + 9, y + 9, TEXT);
        graphics.drawString(font,
                Component.translatable("profession.stonebanner." + npc.hudProfession().serializedName()),
                x + 9, y + 21, MUTED);

        int health = percentage(npc.getHealth(), npc.getMaxHealth());
        drawBar(graphics, font, x + 9, y + 39, width - 18,
                Component.translatable("hud.stonebanner.npc.health"), health, healthColor(health));
        drawBar(graphics, font, x + 9, y + 60, width - 18,
                Component.translatable("hud.stonebanner.npc.hunger"), npc.hudHunger(), pressureColor(npc.hudHunger(), 60, 85));
        drawBar(graphics, font, x + 9, y + 81, width - 18,
                Component.translatable("hud.stonebanner.npc.fatigue"), npc.hudFatigue(), pressureColor(npc.hudFatigue(), 65, 90));
        drawBar(graphics, font, x + 9, y + 102, width - 18,
                Component.translatable("hud.stonebanner.npc.danger"), npc.hudDanger(), pressureColor(npc.hudDanger(), 50, 80));

        WorkType workType = npc.hudWorkType();
        Component activity = workType == null
                ? Component.translatable("brain_state.stonebanner." + npc.brainState().serializedName())
                : Component.translatable("work_type.stonebanner." + workType.serializedName());
        graphics.drawString(font, Component.translatable("hud.stonebanner.npc.now"), x + 9, y + 128, MUTED);
        graphics.drawString(font, activity, x + 49, y + 128, ACCENT);
    }

    private static void drawBar(GuiGraphics graphics, Font font, int x, int y, int width,
                                Component label, int percent, int fillColor) {
        int clamped = Math.max(0, Math.min(100, percent));
        graphics.drawString(font, label, x, y, MUTED);
        Component value = Component.literal(clamped + "%");
        graphics.drawString(font, value, x + width - font.width(value), y, TEXT);

        int top = y + 11;
        int bottom = top + 5;
        graphics.fill(x, top, x + width, bottom, BAR_TRACK);
        int fillWidth = Math.round(width * (clamped / 100.0F));
        if (fillWidth > 0) {
            graphics.fill(x, top, x + fillWidth, bottom, fillColor);
        }
    }

    private static void renderAlerts(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenWidth) {
        List<Alert> alerts = new ArrayList<>();
        int health = percentage(npc.getHealth(), npc.getMaxHealth());
        if (npc.hudDanger() >= CitizenNeeds.CRITICAL_DANGER_THRESHOLD) {
            alerts.add(new Alert(Component.translatable("hud.stonebanner.alert.danger"), DANGER));
        }
        if (health <= 30) {
            alerts.add(new Alert(Component.translatable("hud.stonebanner.alert.low_health", npc.getDisplayName()), DANGER));
        }
        if (npc.hudHunger() >= CitizenNeeds.CRITICAL_HUNGER_THRESHOLD) {
            alerts.add(new Alert(Component.translatable("hud.stonebanner.alert.hunger", npc.getDisplayName()), WARNING));
        }
        if (npc.hudFatigue() >= CitizenNeeds.CRITICAL_FATIGUE_THRESHOLD) {
            alerts.add(new Alert(Component.translatable("hud.stonebanner.alert.fatigue", npc.getDisplayName()), WARNING));
        }
        if (alerts.isEmpty()) {
            return;
        }

        int width = 222;
        int lineHeight = 17;
        int height = 25 + alerts.size() * lineHeight;
        int x = screenWidth - width - 8;
        int y = 43;
        panel(graphics, x, y, width, height, false);
        graphics.drawString(font,
                Component.translatable("hud.stonebanner.alerts").copy().withStyle(ChatFormatting.BOLD),
                x + 8, y + 8, TEXT);

        int lineY = y + 23;
        for (Alert alert : alerts) {
            graphics.fill(x + 7, lineY, x + 10, lineY + 10, alert.color());
            graphics.drawString(font, alert.message(), x + 15, lineY + 1, alert.color());
            lineY += lineHeight;
        }
    }

    private static void renderContextBar(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                         HumanNpcEntity selectedNpc) {
        int y = screenHeight - 32;
        DesignationType designation = DesignationController.activeType().orElse(null);

        List<Chip> chips = new ArrayList<>();
        if (designation != null) {
            chips.add(new Chip(Component.translatable("designation.stonebanner." + designation.serializedName()), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.drag"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.next_tool"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.exit"), true));
        } else if (selectedNpc != null) {
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.move"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.stop"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.work"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.priorities"), false));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.health"), false));
        } else {
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.designations"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.control_mode"), true));
            chips.add(new Chip(Component.translatable("hud.stonebanner.context.camera"), true));
        }

        int totalWidth = 12;
        for (Chip chip : chips) {
            totalWidth += font.width(chip.label()) + 18;
        }
        totalWidth += Math.max(0, chips.size() - 1) * 3;
        totalWidth = Math.min(totalWidth, screenWidth - 16);
        int x = Math.max(8, (screenWidth - totalWidth) / 2);
        panel(graphics, x, y, totalWidth, 24, designation != null || selectedNpc != null);

        int cursor = x + 6;
        for (Chip chip : chips) {
            int chipWidth = font.width(chip.label()) + 14;
            if (cursor + chipWidth > x + totalWidth - 5) {
                break;
            }
            graphics.fill(cursor, y + 4, cursor + chipWidth, y + 20,
                    chip.enabled() ? 0xBB24282D : 0x88303335);
            graphics.renderOutline(cursor, y + 4, chipWidth, 16,
                    chip.enabled() ? BORDER_ACTIVE : 0x665A5650);
            graphics.drawString(font, chip.label(), cursor + 7, y + 8,
                    chip.enabled() ? TEXT : 0xFF77736D);
            cursor += chipWidth + 3;
        }
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, active ? BORDER_ACTIVE : BORDER);
    }

    private static int percentage(float value, float maximum) {
        if (maximum <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(value / maximum * 100.0F)));
    }

    private static int healthColor(int percent) {
        if (percent <= 30) {
            return DANGER;
        }
        if (percent <= 60) {
            return WARNING;
        }
        return GOOD;
    }

    private static int pressureColor(int percent, int warningThreshold, int dangerThreshold) {
        if (percent >= dangerThreshold) {
            return DANGER;
        }
        if (percent >= warningThreshold) {
            return WARNING;
        }
        return GOOD;
    }

    private record Chip(Component label, boolean enabled) {
    }

    private record Alert(Component message, int color) {
    }
}
