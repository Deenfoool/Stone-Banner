package dev.stonebanner.client.hud;

import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.client.control.ExcavationOverlayState;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-focused tactical HUD built from the vanilla Minecraft font and item sprites.
 * The world centre intentionally stays clear; panels only occupy the screen edges.
 */
public final class StoneBannerHudRenderer {
    private static final int PANEL_OUTER = 0xE0101010;
    private static final int PANEL_INNER = 0xDB242424;
    private static final int PANEL_SHADOW = 0xB0000000;
    private static final int PANEL_HIGHLIGHT = 0xFF696969;
    private static final int PANEL_LOWLIGHT = 0xFF080808;
    private static final int SLOT = 0xB8131313;
    private static final int SLOT_DISABLED = 0x9B1A1A1A;
    private static final int BORDER_ACTIVE = 0xFFE2B85C;
    private static final int TEXT = 0xFFF7F7F7;
    private static final int MUTED = 0xFFB8B8B8;
    private static final int ACCENT = 0xFFFFD36A;
    private static final int GOOD = 0xFF67C96B;
    private static final int WARNING = 0xFFFFC84A;
    private static final int DANGER = 0xFFFF5A52;
    private static final int BAR_TRACK = 0xFF101010;
    private static final int BAR_HIGHLIGHT = 0x663F3F3F;

    private static final ItemStack BANNER_ICON = new ItemStack(Items.WHITE_BANNER);
    private static final ItemStack SETTLEMENT_ICON = new ItemStack(Items.BELL);
    private static final ItemStack POPULATION_ICON = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack FOOD_ICON = new ItemStack(Items.BREAD);
    private static final ItemStack TREASURY_ICON = new ItemStack(Items.EMERALD);
    private static final ItemStack PROSPERITY_ICON = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack MATERIALS_ICON = new ItemStack(Items.BRICKS);
    private static final ItemStack CITIZEN_ICON = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack HEALTH_ICON = new ItemStack(Items.APPLE);
    private static final ItemStack HUNGER_ICON = new ItemStack(Items.BREAD);
    private static final ItemStack FATIGUE_ICON = new ItemStack(Items.BLUE_BED);
    private static final ItemStack DANGER_ICON = new ItemStack(Items.SHIELD);
    private static final ItemStack ACTIVITY_ICON = new ItemStack(Items.CLOCK);
    private static final ItemStack ALERT_ICON = new ItemStack(Items.BELL);
    private static final ItemStack HAZARD_ICON = new ItemStack(Items.LAVA_BUCKET);
    private static final ItemStack LOW_HEALTH_ICON = new ItemStack(Items.GLISTERING_MELON_SLICE);
    private static final ItemStack MOVE_ICON = new ItemStack(Items.COMPASS);
    private static final ItemStack STOP_ICON = new ItemStack(Items.BARRIER);
    private static final ItemStack WORK_ICON = new ItemStack(Items.IRON_PICKAXE);
    private static final ItemStack PRIORITIES_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack DETAILS_HEALTH_ICON = new ItemStack(Items.POTION);
    private static final ItemStack DESIGNATIONS_ICON = new ItemStack(Items.IRON_SHOVEL);
    private static final ItemStack CONTROL_MODE_ICON = new ItemStack(Items.REPEATER);
    private static final ItemStack CAMERA_ICON = new ItemStack(Items.SPYGLASS);
    private static final ItemStack DRAG_ICON = new ItemStack(Items.MAP);
    private static final ItemStack NEXT_TOOL_ICON = new ItemStack(Items.SHEARS);
    private static final ItemStack CHOP_ICON = new ItemStack(Items.IRON_AXE);

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
        }
        renderAlerts(graphics, minecraft.font, selected, screenWidth);
        renderContextBar(graphics, minecraft.font, screenWidth, screenHeight, selected);
    }

    private static void renderStrategicBar(GuiGraphics graphics, Font font, int screenWidth) {
        int x = 8;
        int y = 8;
        int height = 34;
        Component settlementLabel = Component.translatable("hud.stonebanner.top.settlement");
        Component settlementValue = Component.translatable("hud.stonebanner.top.not_founded");
        boolean showResources = screenWidth >= 720;

        int width = 122 + metricWidth(font, settlementLabel, settlementValue, 96) + 6;
        if (showResources) {
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.population"), Component.literal("—"), 58) + 4;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.food"), Component.literal("—"), 58) + 4;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.treasury"), Component.literal("—"), 62) + 4;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.prosperity"), Component.literal("—"), 68) + 4;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.materials"), Component.literal("—"), 72) + 4;
        }
        width = Math.min(width, Math.max(1, screenWidth - 16));
        panel(graphics, x, y, width, height, false);

        graphics.renderItem(BANNER_ICON, x + 7, y + 9);
        MutableComponent title = Component.literal("STONE & BANNER").withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, title, x + 28, y + 13, ACCENT, true);

        int cursor = x + 122;
        cursor = metric(graphics, font, cursor, y + 4, SETTLEMENT_ICON,
                settlementLabel, settlementValue, MUTED, 96);

        if (showResources) {
            cursor = metric(graphics, font, cursor, y + 4, POPULATION_ICON,
                    Component.translatable("hud.stonebanner.top.population"), Component.literal("—"), TEXT, 58);
            cursor = metric(graphics, font, cursor, y + 4, FOOD_ICON,
                    Component.translatable("hud.stonebanner.top.food"), Component.literal("—"), TEXT, 58);
            cursor = metric(graphics, font, cursor, y + 4, TREASURY_ICON,
                    Component.translatable("hud.stonebanner.top.treasury"), Component.literal("—"), TEXT, 62);
            cursor = metric(graphics, font, cursor, y + 4, PROSPERITY_ICON,
                    Component.translatable("hud.stonebanner.top.prosperity"), Component.literal("—"), TEXT, 68);
            metric(graphics, font, cursor, y + 4, MATERIALS_ICON,
                    Component.translatable("hud.stonebanner.top.materials"), Component.literal("—"), TEXT, 72);
        }
    }

    private static int metricWidth(Font font, Component label, Component value, int minimumWidth) {
        return Math.max(minimumWidth, Math.max(font.width(label), font.width(value)) + 30);
    }

    private static int metric(GuiGraphics graphics, Font font, int x, int y, ItemStack icon,
                              Component label, Component value, int valueColor, int minimumWidth) {
        int width = metricWidth(font, label, value, minimumWidth);
        inset(graphics, x, y, width, 26, false);
        graphics.renderItem(icon, x + 4, y + 5);
        graphics.drawString(font, label, x + 23, y + 4, MUTED, false);
        graphics.drawString(font, value, x + 23, y + 15, valueColor, true);
        return x + width + 4;
    }

    private static void renderCitizenCard(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenHeight) {
        int width = 210;
        int height = 151;
        int x = 8;
        int y = Math.max(44, screenHeight - height - 40);
        panel(graphics, x, y, width, height, true);

        inset(graphics, x + 6, y + 6, 22, 24, false);
        graphics.renderItem(CITIZEN_ICON, x + 9, y + 10);
        graphics.drawString(font,
                npc.getDisplayName().copy().withStyle(ChatFormatting.BOLD),
                x + 34, y + 8, TEXT, true);
        graphics.drawString(font,
                Component.translatable("profession.stonebanner." + npc.hudProfession().serializedName()),
                x + 34, y + 20, MUTED, false);

        int health = percentage(npc.getHealth(), npc.getMaxHealth());
        drawBar(graphics, font, x + 8, y + 36, width - 16, HEALTH_ICON,
                Component.translatable("hud.stonebanner.npc.health"), health, healthColor(health));
        drawBar(graphics, font, x + 8, y + 58, width - 16, HUNGER_ICON,
                Component.translatable("hud.stonebanner.npc.hunger"),
                npc.hudHunger(), pressureColor(npc.hudHunger(), 60, 85));
        drawBar(graphics, font, x + 8, y + 80, width - 16, FATIGUE_ICON,
                Component.translatable("hud.stonebanner.npc.fatigue"),
                npc.hudFatigue(), pressureColor(npc.hudFatigue(), 65, 90));
        drawBar(graphics, font, x + 8, y + 102, width - 16, DANGER_ICON,
                Component.translatable("hud.stonebanner.npc.danger"),
                npc.hudDanger(), pressureColor(npc.hudDanger(), 50, 80));

        WorkType workType = npc.hudWorkType();
        Component activity = workType == null
                ? Component.translatable("brain_state.stonebanner." + npc.brainState().serializedName())
                : Component.translatable("work_type.stonebanner." + workType.serializedName());
        graphics.renderItem(ACTIVITY_ICON, x + 8, y + 130);
        graphics.drawString(font, Component.translatable("hud.stonebanner.npc.now"), x + 29, y + 128, MUTED, false);
        graphics.drawString(font, activity, x + 29, y + 139, ACCENT, false);
    }

    private static void drawBar(GuiGraphics graphics, Font font, int x, int y, int width, ItemStack icon,
                                Component label, int percent, int fillColor) {
        int clamped = Math.max(0, Math.min(100, percent));
        graphics.renderItem(icon, x, y + 1);

        int contentX = x + 22;
        int contentWidth = width - 22;
        graphics.drawString(font, label, contentX, y, MUTED, false);
        Component value = Component.literal(clamped + "%");
        graphics.drawString(font, value, contentX + contentWidth - font.width(value), y, TEXT, true);

        int top = y + 12;
        int bottom = top + 5;
        graphics.fill(contentX, top, contentX + contentWidth, bottom, BAR_TRACK);
        graphics.hLine(contentX + 1, contentX + contentWidth - 2, top, BAR_HIGHLIGHT);
        int fillWidth = Math.round(contentWidth * (clamped / 100.0F));
        if (fillWidth > 0) {
            graphics.fill(contentX + 1, top + 1, contentX + fillWidth, bottom - 1, fillColor);
        }
    }

    private static void renderAlerts(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenWidth) {
        List<Alert> alerts = new ArrayList<>();

        long hazardPlans = ExcavationOverlayState.plans().stream()
                .filter(plan -> plan.hazardPaused())
                .count();
        if (hazardPlans > 0) {
            alerts.add(new Alert(HAZARD_ICON,
                    Component.translatable("hud.stonebanner.alert.excavation_hazard", hazardPlans), DANGER));
        }

        if (npc != null) {
            int health = percentage(npc.getHealth(), npc.getMaxHealth());
            if (npc.hudDanger() >= CitizenNeeds.CRITICAL_DANGER_THRESHOLD) {
                alerts.add(new Alert(DANGER_ICON,
                        Component.translatable("hud.stonebanner.alert.danger"), DANGER));
            }
            if (health <= 30) {
                alerts.add(new Alert(LOW_HEALTH_ICON,
                        Component.translatable("hud.stonebanner.alert.low_health", npc.getDisplayName()), DANGER));
            }
            if (npc.hudHunger() >= CitizenNeeds.CRITICAL_HUNGER_THRESHOLD) {
                alerts.add(new Alert(HUNGER_ICON,
                        Component.translatable("hud.stonebanner.alert.hunger", npc.getDisplayName()), WARNING));
            }
            if (npc.hudFatigue() >= CitizenNeeds.CRITICAL_FATIGUE_THRESHOLD) {
                alerts.add(new Alert(FATIGUE_ICON,
                        Component.translatable("hud.stonebanner.alert.fatigue", npc.getDisplayName()), WARNING));
            }
        }
        if (alerts.isEmpty()) {
            return;
        }

        int width = Math.min(252, screenWidth - 16);
        int lineHeight = 21;
        int height = 29 + alerts.size() * lineHeight;
        int x = screenWidth - width - 8;
        int y = 47;
        panel(graphics, x, y, width, height, false);
        graphics.renderItem(ALERT_ICON, x + 7, y + 7);
        graphics.drawString(font,
                Component.translatable("hud.stonebanner.alerts").copy().withStyle(ChatFormatting.BOLD),
                x + 29, y + 11, TEXT, true);

        int lineY = y + 28;
        for (Alert alert : alerts) {
            inset(graphics, x + 6, lineY, width - 12, 19, false);
            graphics.renderItem(alert.icon(), x + 8, lineY + 1);
            graphics.drawString(font, alert.message(), x + 29, lineY + 5, alert.color(), true);
            lineY += lineHeight;
        }
    }

    private static void renderContextBar(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                         HumanNpcEntity selectedNpc) {
        int y = screenHeight - 36;
        DesignationType designation = DesignationController.activeType().orElse(null);

        List<Chip> chips = new ArrayList<>();
        if (designation != null) {
            chips.add(new Chip(designationIcon(designation),
                    Component.translatable("designation.stonebanner." + designation.serializedName()), true));
            chips.add(new Chip(DRAG_ICON, Component.translatable("hud.stonebanner.context.drag"), true));
            chips.add(new Chip(NEXT_TOOL_ICON, Component.translatable("hud.stonebanner.context.next_tool"), true));
            chips.add(new Chip(STOP_ICON, Component.translatable("hud.stonebanner.context.exit"), true));
        } else if (selectedNpc != null) {
            chips.add(new Chip(MOVE_ICON, Component.translatable("hud.stonebanner.context.move"), true));
            chips.add(new Chip(STOP_ICON, Component.translatable("hud.stonebanner.context.stop"), true));
            chips.add(new Chip(WORK_ICON, Component.translatable("hud.stonebanner.context.work"), true));
            chips.add(new Chip(PRIORITIES_ICON, Component.translatable("hud.stonebanner.context.priorities"), false));
            chips.add(new Chip(DETAILS_HEALTH_ICON, Component.translatable("hud.stonebanner.context.health"), false));
        } else {
            chips.add(new Chip(DESIGNATIONS_ICON, Component.translatable("hud.stonebanner.context.designations"), true));
            chips.add(new Chip(CONTROL_MODE_ICON, Component.translatable("hud.stonebanner.context.control_mode"), true));
            chips.add(new Chip(CAMERA_ICON, Component.translatable("hud.stonebanner.context.camera"), true));
        }

        int totalWidth = 12;
        for (Chip chip : chips) {
            totalWidth += font.width(chip.label()) + 34;
        }
        totalWidth += Math.max(0, chips.size() - 1) * 3;
        totalWidth = Math.min(totalWidth, screenWidth - 16);
        int x = Math.max(8, (screenWidth - totalWidth) / 2);
        panel(graphics, x, y, totalWidth, 28, designation != null || selectedNpc != null);

        int cursor = x + 6;
        for (Chip chip : chips) {
            int chipWidth = font.width(chip.label()) + 30;
            if (cursor + chipWidth > x + totalWidth - 5) {
                break;
            }
            inset(graphics, cursor, y + 4, chipWidth, 20, chip.enabled());
            graphics.renderItem(chip.icon(), cursor + 2, y + 6);
            graphics.drawString(font, chip.label(), cursor + 21, y + 10,
                    chip.enabled() ? TEXT : 0xFF858585, chip.enabled());
            cursor += chipWidth + 3;
        }
    }

    private static ItemStack designationIcon(DesignationType type) {
        return switch (type) {
            case CHOP -> CHOP_ICON;
            case MINE, EXCAVATE, TUNNEL -> WORK_ICON;
            case CLEAR -> DESIGNATIONS_ICON;
            case CANCEL -> STOP_ICON;
        };
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, PANEL_SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL_OUTER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL_INNER);
        graphics.hLine(x + 1, x + width - 2, y + 1, PANEL_HIGHLIGHT);
        graphics.vLine(x + 1, y + 1, y + height - 2, PANEL_HIGHLIGHT);
        graphics.hLine(x + 1, x + width - 1, y + height - 1, PANEL_LOWLIGHT);
        graphics.vLine(x + width - 1, y + 1, y + height - 1, PANEL_LOWLIGHT);
        if (active) {
            graphics.renderOutline(x + 2, y + 2, width - 4, height - 4, BORDER_ACTIVE);
        }
    }

    private static void inset(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? SLOT : SLOT_DISABLED);
        graphics.hLine(x, x + width - 1, y, PANEL_LOWLIGHT);
        graphics.vLine(x, y, y + height - 1, PANEL_LOWLIGHT);
        graphics.hLine(x + 1, x + width - 1, y + height - 1, active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
        graphics.vLine(x + width - 1, y + 1, y + height - 1, active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
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

    private record Chip(ItemStack icon, Component label, boolean enabled) {
    }

    private record Alert(ItemStack icon, Component message, int color) {
    }
}
