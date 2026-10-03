package dev.stonebanner.client.hud;

import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.client.control.ExcavationOverlayState;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** RimWorld-inspired tactical HUD built with Minecraft font and item sprites. */
public final class StoneBannerHudRenderer {
    private static final int PANEL_OUTER = 0xF00D0D0D;
    private static final int PANEL_INNER = 0xE3252525;
    private static final int PANEL_SHADOW = 0xB0000000;
    private static final int PANEL_HIGHLIGHT = 0xFF67625A;
    private static final int PANEL_LOWLIGHT = 0xFF080808;
    private static final int SLOT = 0xE01A1917;
    private static final int SLOT_HOVER = 0xF03B372F;
    private static final int SLOT_DISABLED = 0xC0161616;
    private static final int BORDER_ACTIVE = 0xFFE1B75B;
    private static final int TEXT = 0xFFF7F4EA;
    private static final int MUTED = 0xFFB9B3A8;
    private static final int ACCENT = 0xFFFFD36A;
    private static final int GOOD = 0xFF67C96B;
    private static final int WARNING = 0xFFFFC84A;
    private static final int DANGER = 0xFFFF5A52;
    private static final int BAR_TRACK = 0xFF101010;
    private static final int BAR_HIGHLIGHT = 0x663F3F3F;
    private static final int TOP_OUTER = 0xB80D0D0D;
    private static final int TOP_INNER = 0xA8242424;

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
    private static final ItemStack CONSTRUCTION_TAB_ICON = new ItemStack(Items.BRICKS);
    private static final ItemStack HISTORY_TAB_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack RESEARCH_TAB_ICON = new ItemStack(Items.ENCHANTING_TABLE);
    private static final ItemStack PAUSE_ICON = new ItemStack(Items.BARRIER);
    private static final ItemStack PLAY_ICON = new ItemStack(Items.LIME_DYE);

    private static boolean citizenPanelExpanded = true;
    private static int lastCitizenId = Integer.MIN_VALUE;

    private StoneBannerHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.font == null || screenWidth <= 0 || screenHeight <= 0) return;

        HumanNpcEntity selected = CitizenSelectionController.selected().orElse(null);
        if (selected != null && selected.getId() != lastCitizenId) {
            lastCitizenId = selected.getId();
            citizenPanelExpanded = true;
        } else if (selected == null) {
            lastCitizenId = Integer.MIN_VALUE;
        }

        renderStrategicBar(graphics, minecraft.font, screenWidth);
        renderTimeControls(graphics, minecraft.font, screenWidth);
        renderAlerts(graphics, minecraft.font, selected, screenWidth);
        renderContextBar(graphics, minecraft.font, screenWidth, screenHeight, selected);
        renderBottomToolbar(graphics, minecraft.font, screenWidth, screenHeight, selected);
        if (selected != null) renderCitizenInspector(graphics, minecraft.font, selected, screenHeight);
    }

    public static HudAction actionAt(double mouseX, double mouseY, int screenWidth, int screenHeight,
                                     boolean hasSelectedCitizen) {
        if (hasSelectedCitizen) {
            if (RimHudLayout.citizenHeader(screenHeight).contains(mouseX, mouseY)) return HudAction.TOGGLE_CITIZEN;
            if (citizenPanelExpanded && RimHudLayout.citizenPanel(screenHeight).contains(mouseX, mouseY)) {
                return HudAction.OPEN_CITIZEN;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (RimHudLayout.timeButton(screenWidth, i).contains(mouseX, mouseY)) {
                return switch (i) {
                    case 0 -> HudAction.TIME_PAUSE;
                    case 1 -> HudAction.TIME_NORMAL;
                    case 2 -> HudAction.TIME_DOUBLE;
                    default -> HudAction.TIME_TRIPLE;
                };
            }
        }
        int tabCount = BottomTab.values().length;
        if (RimHudLayout.bottomButton(screenWidth, screenHeight, 0, tabCount).contains(mouseX, mouseY)) {
            return HudAction.CONSTRUCTION;
        }
        if (hasSelectedCitizen
                && RimHudLayout.bottomButton(screenWidth, screenHeight, 1, tabCount).contains(mouseX, mouseY)) {
            return HudAction.OPEN_WORK;
        }
        return HudAction.NONE;
    }

    public static void toggleCitizenPanel() {
        citizenPanelExpanded = !citizenPanelExpanded;
    }

    private static void renderStrategicBar(GuiGraphics graphics, Font font, int screenWidth) {
        int x = 8;
        int y = 8;
        int height = 28;
        Component settlementLabel = Component.translatable("hud.stonebanner.top.settlement");
        Component settlementValue = Component.translatable("hud.stonebanner.top.not_founded");
        boolean showResources = screenWidth >= 720;
        int width = 112 + metricWidth(font, settlementLabel, settlementValue, 82) + 6;
        if (showResources) {
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.population"), Component.literal("—"), 50) + 3;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.food"), Component.literal("—"), 50) + 3;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.treasury"), Component.literal("—"), 54) + 3;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.prosperity"), Component.literal("—"), 60) + 3;
            width += metricWidth(font, Component.translatable("hud.stonebanner.top.materials"), Component.literal("—"), 66) + 3;
        }
        width = Math.min(width, Math.max(1, screenWidth - RimHudLayout.TIME_PANEL_WIDTH - 28));
        topPanel(graphics, x, y, width, height);
        graphics.renderItem(BANNER_ICON, x + 5, y + 6);
        graphics.drawString(font, Component.literal("STONE & BANNER").withStyle(ChatFormatting.BOLD),
                x + 25, y + 10, ACCENT, true);

        int cursor = x + 112;
        cursor = metric(graphics, font, cursor, y + 3, SETTLEMENT_ICON, settlementLabel, settlementValue, MUTED, 82);
        if (showResources) {
            cursor = metric(graphics, font, cursor, y + 4, POPULATION_ICON,
                    Component.translatable("hud.stonebanner.top.population"), Component.literal("—"), TEXT, 50);
            cursor = metric(graphics, font, cursor, y + 4, FOOD_ICON,
                    Component.translatable("hud.stonebanner.top.food"), Component.literal("—"), TEXT, 50);
            cursor = metric(graphics, font, cursor, y + 4, TREASURY_ICON,
                    Component.translatable("hud.stonebanner.top.treasury"), Component.literal("—"), TEXT, 54);
            cursor = metric(graphics, font, cursor, y + 4, PROSPERITY_ICON,
                    Component.translatable("hud.stonebanner.top.prosperity"), Component.literal("—"), TEXT, 60);
            metric(graphics, font, cursor, y + 4, MATERIALS_ICON,
                    Component.translatable("hud.stonebanner.top.materials"), Component.literal("—"), TEXT, 66);
        }
    }

    private static int metricWidth(Font font, Component label, Component value, int minimumWidth) {
        return Math.max(minimumWidth, Math.max(font.width(label), font.width(value)) + 30);
    }

    private static int metric(GuiGraphics graphics, Font font, int x, int y, ItemStack icon,
                              Component label, Component value, int valueColor, int minimumWidth) {
        int width = metricWidth(font, label, value, minimumWidth);
        inset(graphics, x, y, width, 22, false);
        graphics.renderItem(icon, x + 3, y + 3);
        graphics.drawString(font, label, x + 21, y + 2, MUTED, false);
        graphics.drawString(font, value, x + 21, y + 12, valueColor, true);
        return x + width + 3;
    }

    private static void renderTimeControls(GuiGraphics graphics, Font font, int screenWidth) {
        RimHudLayout.Rect bounds = RimHudLayout.timePanel(screenWidth);
        topPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        GameSpeedController.Speed selected = GameSpeedController.speed();
        timeButton(graphics, font, RimHudLayout.timeButton(screenWidth, 0), PAUSE_ICON,
                Component.translatable("hud.stonebanner.time.pause"), selected == GameSpeedController.Speed.PAUSED);
        timeButton(graphics, font, RimHudLayout.timeButton(screenWidth, 1), PLAY_ICON,
                Component.translatable("hud.stonebanner.time.start"), selected == GameSpeedController.Speed.NORMAL);
        timeButton(graphics, font, RimHudLayout.timeButton(screenWidth, 2), ItemStack.EMPTY,
                Component.literal("x2"), selected == GameSpeedController.Speed.DOUBLE);
        timeButton(graphics, font, RimHudLayout.timeButton(screenWidth, 3), ItemStack.EMPTY,
                Component.literal("x3"), selected == GameSpeedController.Speed.TRIPLE);
    }

    private static void timeButton(GuiGraphics graphics, Font font, RimHudLayout.Rect bounds, ItemStack icon,
                                   Component label, boolean active) {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                active ? SLOT_HOVER : SLOT);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
        int textX = bounds.x() + 5;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, bounds.x() + 3, bounds.y() + 3);
            textX += 17;
        }
        graphics.drawString(font, label, textX, bounds.y() + 7, active ? ACCENT : TEXT, active);
    }

    private static void renderCitizenInspector(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenHeight) {
        RimHudLayout.Rect header = RimHudLayout.citizenHeader(screenHeight);
        if (citizenPanelExpanded) renderCitizenPanel(graphics, font, npc, RimHudLayout.citizenPanel(screenHeight));

        panel(graphics, header.x(), header.y(), header.width(), header.height(), true);
        inset(graphics, header.x() + 5, header.y() + 4, 22, 22, true);
        graphics.renderItem(CITIZEN_ICON, header.x() + 8, header.y() + 7);
        graphics.drawString(font, npc.getDisplayName().copy().withStyle(ChatFormatting.BOLD),
                header.x() + 33, header.y() + 5, TEXT, true);
        graphics.drawString(font, Component.translatable("profession.stonebanner." + npc.hudProfession().serializedName()),
                header.x() + 33, header.y() + 17, MUTED, false);
        Component toggle = Component.translatable(citizenPanelExpanded
                ? "hud.stonebanner.npc.collapse" : "hud.stonebanner.npc.expand");
        graphics.drawString(font, toggle, header.x() + header.width() - font.width(toggle) - 8,
                header.y() + 11, ACCENT, true);
    }

    private static void renderCitizenPanel(GuiGraphics graphics, Font font, HumanNpcEntity npc,
                                           RimHudLayout.Rect bounds) {
        if (bounds.height() < 100) return;
        panel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height() + 2, true);
        int health = percentage(npc.getHealth(), npc.getMaxHealth());
        drawBar(graphics, font, bounds.x() + 8, bounds.y() + 7, bounds.width() - 16, HEALTH_ICON,
                Component.translatable("hud.stonebanner.npc.health"), health, healthColor(health));
        drawBar(graphics, font, bounds.x() + 8, bounds.y() + 29, bounds.width() - 16, HUNGER_ICON,
                Component.translatable("hud.stonebanner.npc.hunger"), npc.hudHunger(), pressureColor(npc.hudHunger(), 60, 85));
        drawBar(graphics, font, bounds.x() + 8, bounds.y() + 51, bounds.width() - 16, FATIGUE_ICON,
                Component.translatable("hud.stonebanner.npc.fatigue"), npc.hudFatigue(), pressureColor(npc.hudFatigue(), 65, 90));
        drawBar(graphics, font, bounds.x() + 8, bounds.y() + 73, bounds.width() - 16, DANGER_ICON,
                Component.translatable("hud.stonebanner.npc.danger"), npc.hudDanger(), pressureColor(npc.hudDanger(), 50, 80));

        WorkType workType = npc.hudWorkType();
        Component activity = workType == null
                ? Component.translatable("brain_state.stonebanner." + npc.brainState().serializedName())
                : Component.translatable("work_type.stonebanner." + workType.serializedName());
        graphics.renderItem(ACTIVITY_ICON, bounds.x() + 8, bounds.y() + 103);
        graphics.drawString(font, Component.translatable("hud.stonebanner.npc.now"), bounds.x() + 29, bounds.y() + 100, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(activity.getString(), 105),
                bounds.x() + 29, bounds.y() + 112, ACCENT, false);

        int detailsWidth = 66;
        int detailsX = bounds.x() + bounds.width() - detailsWidth - 7;
        inset(graphics, detailsX, bounds.y() + 101, detailsWidth, 24, true);
        graphics.renderItem(HISTORY_TAB_ICON, detailsX + 3, bounds.y() + 105);
        graphics.drawString(font, Component.translatable("hud.stonebanner.npc.details"),
                detailsX + 22, bounds.y() + 109, TEXT, true);
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
        if (fillWidth > 0) graphics.fill(contentX + 1, top + 1, contentX + fillWidth, bottom - 1, fillColor);
    }

    private static void renderAlerts(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenWidth) {
        List<Alert> alerts = new ArrayList<>();
        long hazardPlans = ExcavationOverlayState.plans().stream().filter(plan -> plan.hazardPaused()).count();
        if (hazardPlans > 0) {
            alerts.add(new Alert(HAZARD_ICON, Component.translatable("hud.stonebanner.alert.excavation_hazard", hazardPlans), DANGER));
        }
        if (npc != null) {
            int health = percentage(npc.getHealth(), npc.getMaxHealth());
            if (npc.hudDanger() >= CitizenNeeds.CRITICAL_DANGER_THRESHOLD) {
                alerts.add(new Alert(DANGER_ICON, Component.translatable("hud.stonebanner.alert.danger"), DANGER));
            }
            if (health <= 30) {
                alerts.add(new Alert(LOW_HEALTH_ICON, Component.translatable("hud.stonebanner.alert.low_health", npc.getDisplayName()), DANGER));
            }
            if (npc.hudHunger() >= CitizenNeeds.CRITICAL_HUNGER_THRESHOLD) {
                alerts.add(new Alert(HUNGER_ICON, Component.translatable("hud.stonebanner.alert.hunger", npc.getDisplayName()), WARNING));
            }
            if (npc.hudFatigue() >= CitizenNeeds.CRITICAL_FATIGUE_THRESHOLD) {
                alerts.add(new Alert(FATIGUE_ICON, Component.translatable("hud.stonebanner.alert.fatigue", npc.getDisplayName()), WARNING));
            }
        }
        if (alerts.isEmpty()) return;

        int width = Math.min(252, screenWidth - 16);
        int lineHeight = 21;
        int height = 29 + alerts.size() * lineHeight;
        int x = screenWidth - width - 8;
        int y = 47;
        panel(graphics, x, y, width, height, false);
        graphics.renderItem(ALERT_ICON, x + 7, y + 7);
        graphics.drawString(font, Component.translatable("hud.stonebanner.alerts").copy().withStyle(ChatFormatting.BOLD),
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
        int y = RimHudLayout.contextY(screenHeight);
        DesignationType designation = DesignationController.activeType().orElse(null);
        List<Chip> chips = new ArrayList<>();
        if (designation != null) {
            chips.add(new Chip(designationIcon(designation), Component.translatable("designation.stonebanner." + designation.serializedName()), true));
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
        for (Chip chip : chips) totalWidth += font.width(chip.label()) + 34;
        totalWidth += Math.max(0, chips.size() - 1) * 3;
        totalWidth = Math.min(totalWidth, screenWidth - 16);
        int x = Math.max(8, (screenWidth - totalWidth) / 2);
        panel(graphics, x, y, totalWidth, RimHudLayout.CONTEXT_HEIGHT, designation != null || selectedNpc != null);
        int cursor = x + 6;
        for (Chip chip : chips) {
            int chipWidth = font.width(chip.label()) + 30;
            if (cursor + chipWidth > x + totalWidth - 5) break;
            inset(graphics, cursor, y + 4, chipWidth, 22, chip.enabled());
            graphics.renderItem(chip.icon(), cursor + 3, y + 7);
            graphics.drawString(font, chip.label(), cursor + 22, y + 11,
                    chip.enabled() ? TEXT : 0xFF858585, chip.enabled());
            cursor += chipWidth + 3;
        }
    }

    private static void renderBottomToolbar(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                            HumanNpcEntity selectedNpc) {
        RimHudLayout.Rect bar = RimHudLayout.bottomBar(screenWidth, screenHeight);
        graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), PANEL_OUTER);
        graphics.hLine(0, Math.max(0, screenWidth - 1), bar.y(), PANEL_HIGHLIGHT);
        BottomTab[] tabs = BottomTab.values();
        for (int i = 0; i < tabs.length; i++) {
            BottomTab tab = tabs[i];
            RimHudLayout.Rect button = RimHudLayout.bottomButton(screenWidth, screenHeight, i, tabs.length);
            boolean enabled = tab == BottomTab.CONSTRUCTION || (tab == BottomTab.WORK && selectedNpc != null);
            boolean active = tab == BottomTab.CONSTRUCTION && DesignationController.isActive();
            toolbarButton(graphics, font, button, tab.icon, Component.translatable(tab.translationKey), enabled, active);
        }
    }

    private static void toolbarButton(GuiGraphics graphics, Font font, RimHudLayout.Rect bounds, ItemStack icon,
                                      Component label, boolean enabled, boolean active) {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                active ? SLOT_HOVER : enabled ? SLOT : SLOT_DISABLED);
        graphics.hLine(bounds.x(), bounds.x() + bounds.width() - 1, bounds.y(), active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
        graphics.vLine(bounds.x(), bounds.y(), bounds.y() + bounds.height() - 1, PANEL_HIGHLIGHT);
        graphics.hLine(bounds.x(), bounds.x() + bounds.width() - 1, bounds.y() + bounds.height() - 1, PANEL_LOWLIGHT);
        if (active) graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 2, BORDER_ACTIVE);
        graphics.renderItem(icon, bounds.x() + 5, bounds.y() + 5);
        int available = Math.max(1, bounds.width() - 29);
        int textWidth = Math.max(1, font.width(label));
        float scale = Math.min(1.0F, available / (float) textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x() + 25, bounds.y() + 9, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, label, 0, 0, enabled ? TEXT : 0xFF77736C, enabled);
        graphics.pose().popPose();
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
        if (active) graphics.renderOutline(x + 2, y + 2, width - 4, height - 4, BORDER_ACTIVE);
    }

    private static void topPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x70000000);
        graphics.fill(x, y, x + width, y + height, TOP_OUTER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, TOP_INNER);
        graphics.hLine(x + 1, x + width - 2, y + 1, PANEL_HIGHLIGHT);
        graphics.vLine(x + 1, y + 1, y + height - 2, PANEL_HIGHLIGHT);
        graphics.hLine(x + 1, x + width - 1, y + height - 1, PANEL_LOWLIGHT);
        graphics.vLine(x + width - 1, y + 1, y + height - 1, PANEL_LOWLIGHT);
    }

    private static void inset(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? SLOT : SLOT_DISABLED);
        graphics.hLine(x, x + width - 1, y, PANEL_LOWLIGHT);
        graphics.vLine(x, y, y + height - 1, PANEL_LOWLIGHT);
        graphics.hLine(x + 1, x + width - 1, y + height - 1, active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
        graphics.vLine(x + width - 1, y + 1, y + height - 1, active ? BORDER_ACTIVE : PANEL_HIGHLIGHT);
    }

    private static int percentage(float value, float maximum) {
        if (maximum <= 0.0F) return 0;
        return Math.max(0, Math.min(100, Math.round(value / maximum * 100.0F)));
    }

    private static int healthColor(int percent) {
        if (percent <= 30) return DANGER;
        if (percent <= 60) return WARNING;
        return GOOD;
    }

    private static int pressureColor(int percent, int warningThreshold, int dangerThreshold) {
        if (percent >= dangerThreshold) return DANGER;
        if (percent >= warningThreshold) return WARNING;
        return GOOD;
    }

    public enum HudAction {
        NONE,
        CONSTRUCTION,
        OPEN_WORK,
        TOGGLE_CITIZEN,
        OPEN_CITIZEN,
        TIME_PAUSE,
        TIME_NORMAL,
        TIME_DOUBLE,
        TIME_TRIPLE
    }

    private enum BottomTab {
        CONSTRUCTION("hud.stonebanner.menu.construction", CONSTRUCTION_TAB_ICON),
        WORK("hud.stonebanner.menu.work", PRIORITIES_ICON),
        HISTORY("hud.stonebanner.menu.history", HISTORY_TAB_ICON),
        RESEARCH("hud.stonebanner.menu.research", RESEARCH_TAB_ICON);

        private final String translationKey;
        private final ItemStack icon;

        BottomTab(String translationKey, ItemStack icon) {
            this.translationKey = translationKey;
            this.icon = icon;
        }
    }

    private record Chip(ItemStack icon, Component label, boolean enabled) { }
    private record Alert(ItemStack icon, Component message, int color) { }
}
