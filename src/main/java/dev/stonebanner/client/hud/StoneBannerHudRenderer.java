package dev.stonebanner.client.hud;

import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.client.control.ExcavationOverlayState;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.client.control.TunnelExtensionController;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.designation.ExcavationAccessMode;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-focused Stone & Banner HUD.
 *
 * <p>The permanent bottom-most row is the player's real Minecraft hotbar. Management tabs live directly
 * above it and may collapse without hiding inventory. Context tools belong to their management tab instead
 * of occupying the HUD permanently; currently Orders owns the designation hierarchy.</p>
 */
public final class StoneBannerHudRenderer {
    private static final int PANEL_OUTER = 0xF20B0D0F;
    private static final int PANEL_INNER = 0xE71A1D20;
    private static final int PANEL_SHADOW = 0xA0000000;
    private static final int FRAME = 0xFF4E3D2C;
    private static final int FRAME_LIGHT = 0xFF8B6C42;
    private static final int FRAME_DARK = 0xFF16110D;
    private static final int SLOT = 0xE51A1D20;
    private static final int SLOT_HOVER = 0xF02D302F;
    private static final int SLOT_DISABLED = 0xC0131517;
    private static final int BORDER_ACTIVE = 0xFFFFC928;
    private static final int TEXT = 0xFFF4F1E8;
    private static final int MUTED = 0xFFB4AEA3;
    private static final int ACCENT = 0xFFFFD45A;
    private static final int GOOD = 0xFF67C96B;
    private static final int WARNING = 0xFFFFC84A;
    private static final int DANGER = 0xFFFF5A52;
    private static final int BAR_TRACK = 0xFF0F1113;
    private static final int BAR_HIGHLIGHT = 0x66454545;
    private static final int MAP_WATER = 0xFF2D5F91;
    private static final int MAP_LOW = 0xFF315238;
    private static final int MAP_LEVEL = 0xFF4D7447;
    private static final int MAP_HIGH = 0xFF697166;
    private static final int MAP_SAND = 0xFF9A8A5A;
    private static final int MAP_SNOW = 0xFFD4D8D5;

    private static final ItemStack BANNER_ICON = new ItemStack(Items.WHITE_BANNER);
    private static final ItemStack WEATHER_CLEAR_ICON = new ItemStack(Items.SUNFLOWER);
    private static final ItemStack WEATHER_RAIN_ICON = new ItemStack(Items.WATER_BUCKET);
    private static final ItemStack POPULATION_ICON = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack HOUSING_ICON = new ItemStack(Items.OAK_DOOR);
    private static final ItemStack FOOD_ICON = new ItemStack(Items.BREAD);
    private static final ItemStack DEFENSE_ICON = new ItemStack(Items.SHIELD);
    private static final ItemStack WOOD_ICON = new ItemStack(Items.OAK_LOG);
    private static final ItemStack STONE_ICON = new ItemStack(Items.COBBLESTONE);
    private static final ItemStack IRON_ICON = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack GOLD_ICON = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack REDSTONE_ICON = new ItemStack(Items.REDSTONE);
    private static final ItemStack GRAIN_ICON = new ItemStack(Items.WHEAT);
    private static final ItemStack ALERT_ICON = new ItemStack(Items.BELL);
    private static final ItemStack HAZARD_ICON = new ItemStack(Items.LAVA_BUCKET);
    private static final ItemStack LOW_HEALTH_ICON = new ItemStack(Items.GLISTERING_MELON_SLICE);

    private static final ItemStack CITIZEN_ICON = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack HEALTH_ICON = new ItemStack(Items.APPLE);
    private static final ItemStack HUNGER_ICON = new ItemStack(Items.BREAD);
    private static final ItemStack FATIGUE_ICON = new ItemStack(Items.BLUE_BED);
    private static final ItemStack DANGER_ICON = new ItemStack(Items.SHIELD);
    private static final ItemStack COMBAT_ICON = new ItemStack(Items.IRON_SWORD);
    private static final ItemStack CONSTRUCTION_ICON = new ItemStack(Items.BRICKS);
    private static final ItemStack MINING_ICON = new ItemStack(Items.IRON_PICKAXE);
    private static final ItemStack OVERVIEW_ICON = new ItemStack(Items.BOOK);
    private static final ItemStack DETAILS_HEALTH_ICON = new ItemStack(Items.POTION);
    private static final ItemStack SKILLS_ICON = new ItemStack(Items.EXPERIENCE_BOTTLE);
    private static final ItemStack PRIORITIES_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack INVENTORY_ICON = new ItemStack(Items.CHEST);

    private static final ItemStack BUILD_TAB_ICON = new ItemStack(Items.BRICKS);
    private static final ItemStack ORDERS_TAB_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack ZONES_TAB_ICON = new ItemStack(Items.GRASS_BLOCK);
    private static final ItemStack RESEARCH_TAB_ICON = new ItemStack(Items.ENCHANTING_TABLE);
    private static final ItemStack CRAFTING_TAB_ICON = new ItemStack(Items.ANVIL);
    private static final ItemStack CHOP_ICON = new ItemStack(Items.IRON_AXE);
    private static final ItemStack MINE_ICON = new ItemStack(Items.IRON_PICKAXE);
    private static final ItemStack EXCAVATE_ICON = new ItemStack(Items.IRON_SHOVEL);
    private static final ItemStack TUNNEL_ICON = new ItemStack(Items.RAIL);
    private static final ItemStack CLEAR_ICON = new ItemStack(Items.SHEARS);
    private static final ItemStack CANCEL_ICON = new ItemStack(Items.BARRIER);
    private static final ItemStack ACCESS_ICON = new ItemStack(Items.LADDER);
    private static final ItemStack CLOCK_ICON = new ItemStack(Items.CLOCK);
    private static final ItemStack MAP_ICON = new ItemStack(Items.FILLED_MAP);

    private static final MiniMapCache MINI_MAP = new MiniMapCache();

    private static boolean citizenPanelExpanded = true;
    private static boolean bottomDockExpanded = true;
    private static BottomTab activeBottomTab = BottomTab.ORDERS;
    private static int lastCitizenId = Integer.MIN_VALUE;

    private StoneBannerHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.font == null || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        HumanNpcEntity selected = CitizenSelectionController.selected().orElse(null);
        if (selected != null && selected.getId() != lastCitizenId) {
            lastCitizenId = selected.getId();
            citizenPanelExpanded = true;
        } else if (selected == null) {
            lastCitizenId = Integer.MIN_VALUE;
        }

        renderStrategicBar(graphics, minecraft, screenWidth);
        renderAlerts(graphics, minecraft.font, selected, screenWidth);
        renderRightRail(graphics, minecraft, screenWidth, screenHeight);
        renderBottomDock(graphics, minecraft, screenWidth, screenHeight, selected);
        if (selected != null) {
            renderCitizenInspector(graphics, minecraft.font, selected, screenWidth, screenHeight);
        }
    }

    public static HudAction actionAt(double mouseX, double mouseY, int screenWidth, int screenHeight,
                                     boolean hasSelectedCitizen) {
        if (StoneBannerHudLayout.topBar(screenWidth).contains(mouseX, mouseY)) {
            return HudAction.CONSUME;
        }

        if (hasSelectedCitizen) {
            StoneBannerHudLayout.Rect card = StoneBannerHudLayout.citizenCard(screenWidth, screenHeight, citizenPanelExpanded);
            StoneBannerHudLayout.Rect toggle = new StoneBannerHudLayout.Rect(
                    card.x() + card.width() - 27, card.y() + 5, 22, 22
            );
            if (toggle.contains(mouseX, mouseY)) {
                return HudAction.TOGGLE_CITIZEN;
            }
            if (citizenPanelExpanded) {
                for (int index = 0; index < 5; index++) {
                    if (StoneBannerHudLayout.citizenTab(screenWidth, screenHeight, true, index, 5)
                            .contains(mouseX, mouseY)) {
                        return switch (index) {
                            case 0 -> HudAction.OPEN_CITIZEN_OVERVIEW;
                            case 1 -> HudAction.OPEN_CITIZEN_HEALTH;
                            case 2 -> HudAction.OPEN_CITIZEN_SKILLS;
                            case 3 -> HudAction.OPEN_WORK;
                            default -> HudAction.OPEN_CITIZEN_INVENTORY;
                        };
                    }
                }
            }
            if (card.contains(mouseX, mouseY)) {
                return citizenPanelExpanded ? HudAction.OPEN_CITIZEN_OVERVIEW : HudAction.TOGGLE_CITIZEN;
            }
        }

        StoneBannerHudLayout.Rect rightRail = StoneBannerHudLayout.rightRail(screenWidth, screenHeight);
        if (rightRail.contains(mouseX, mouseY)) {
            for (int i = 0; i < 4; i++) {
                if (StoneBannerHudLayout.timeButton(screenWidth, screenHeight, i).contains(mouseX, mouseY)) {
                    return switch (i) {
                        case 0 -> HudAction.TIME_PAUSE;
                        case 1 -> HudAction.TIME_NORMAL;
                        case 2 -> HudAction.TIME_DOUBLE;
                        default -> HudAction.TIME_TRIPLE;
                    };
                }
            }
            for (int i = 0; i < 3; i++) {
                if (StoneBannerHudLayout.layerButton(screenWidth, screenHeight, i).contains(mouseX, mouseY))
                    return switch (i) { case 0 -> HudAction.LAYER_BOUNDARIES; case 1 -> HudAction.LAYER_RESOURCES; default -> HudAction.LAYER_FERTILITY; };
            }
            return HudAction.CONSUME;
        }

        if (StoneBannerHudLayout.bottomToggle(screenWidth, screenHeight, bottomDockExpanded).contains(mouseX, mouseY)) {
            return HudAction.TOGGLE_BOTTOM_DOCK;
        }

        StoneBannerHudLayout.Rect dock = StoneBannerHudLayout.bottomDock(screenWidth, screenHeight, bottomDockExpanded);
        if (dock.contains(mouseX, mouseY)) {
            for (int i = 0; i < StoneBannerHudLayout.HOTBAR_SLOTS; i++) {
                if (StoneBannerHudLayout.hotbarSlot(screenWidth, screenHeight, bottomDockExpanded, i)
                        .contains(mouseX, mouseY)) {
                    return hotbarAction(i);
                }
            }

            if (!bottomDockExpanded) {
                return HudAction.CONSUME;
            }

            BottomTab[] tabs = BottomTab.values();
            for (int i = 0; i < tabs.length; i++) {
                if (StoneBannerHudLayout.bottomTab(screenWidth, screenHeight, i, tabs.length).contains(mouseX, mouseY)) {
                    return switch (tabs[i]) {
                        case BUILD -> HudAction.TAB_BUILD;
                        case ORDERS -> HudAction.TAB_ORDERS;
                        case ZONES -> HudAction.TAB_ZONES;
                        case RESEARCH -> HudAction.TAB_RESEARCH;
                        case CRAFTING -> HudAction.TAB_CRAFTING;
                    };
                }
            }

            if (activeBottomTab == BottomTab.ORDERS) {
                DesignationType[] tools = orderTools();
                DesignationType activeType = DesignationController.activeType().orElse(null);
                boolean showAccessMode = activeType == DesignationType.EXCAVATE;
                boolean showExtend = activeType == DesignationType.TUNNEL || TunnelExtensionController.isActive();
                int slotCount = tools.length + ((showAccessMode || showExtend) ? 1 : 0);
                for (int i = 0; i < tools.length; i++) {
                    if (StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, i, slotCount)
                            .contains(mouseX, mouseY)) {
                        return designationAction(tools[i]);
                    }
                }
                if (showAccessMode
                        && StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, tools.length, slotCount)
                        .contains(mouseX, mouseY)) {
                    return HudAction.CYCLE_EXCAVATION_ACCESS;
                }
                if (showExtend
                        && StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, tools.length, slotCount)
                        .contains(mouseX, mouseY)) {
                    return HudAction.EXTEND_TUNNEL;
                }
            }
            return HudAction.CONSUME;
        }

        StoneBannerHudLayout.Rect alerts = StoneBannerHudLayout.alerts(screenWidth, 3);
        if (alerts.contains(mouseX, mouseY)) {
            return HudAction.CONSUME;
        }
        return HudAction.NONE;
    }

    public static void toggleCitizenPanel() {
        citizenPanelExpanded = !citizenPanelExpanded;
    }

    public static void toggleBottomDock() {
        bottomDockExpanded = !bottomDockExpanded;
    }

    public static void selectBottomTab(int index) {
        BottomTab[] tabs = BottomTab.values();
        if (index >= 0 && index < tabs.length) {
            activeBottomTab = tabs[index];
        }
    }

    public static int hotbarIndex(HudAction action) {
        return switch (action) {
            case HOTBAR_1 -> 0;
            case HOTBAR_2 -> 1;
            case HOTBAR_3 -> 2;
            case HOTBAR_4 -> 3;
            case HOTBAR_5 -> 4;
            case HOTBAR_6 -> 5;
            case HOTBAR_7 -> 6;
            case HOTBAR_8 -> 7;
            case HOTBAR_9 -> 8;
            default -> -1;
        };
    }

    private static void renderStrategicBar(GuiGraphics graphics, Minecraft minecraft, int screenWidth) {
        Font font = minecraft.font;
        StoneBannerHudLayout.Rect bar = StoneBannerHudLayout.topBar(screenWidth);
        panel(graphics, bar.x(), bar.y(), bar.width(), bar.height(), false);

        int leftWidth = Math.min(220, Math.max(165, bar.width() / 4));
        int rightButtonsWidth = 78;
        int vitalsWidth = Math.min(225, Math.max(175, bar.width() / 4));
        int resourcesX = bar.x() + leftWidth + vitalsWidth;
        int resourcesWidth = Math.max(0, bar.width() - leftWidth - vitalsWidth - rightButtonsWidth);

        graphics.renderItem(BANNER_ICON, bar.x() + 8, bar.y() + 7);
        graphics.drawString(font,
                Component.translatable("hud.stonebanner.top.settlement").copy().withStyle(ChatFormatting.BOLD),
                bar.x() + 29, bar.y() + 7, TEXT, true);
        graphics.drawString(font, Component.translatable("hud.stonebanner.top.not_founded"),
                bar.x() + 29, bar.y() + 21, MUTED, false);

        WorldClock clock = worldClock(minecraft);
        int weatherX = bar.x() + Math.max(100, leftWidth - 91);
        graphics.renderItem(clock.weatherIcon(), weatherX, bar.y() + 6);
        graphics.drawString(font, clock.weatherLabel(), weatherX + 20, bar.y() + 6, TEXT, false);
        graphics.drawString(font, Component.translatable("hud.stonebanner.top.day_time", clock.day(), clock.time()),
                weatherX + 20, bar.y() + 20, MUTED, false);

        vDivider(graphics, bar.x() + leftWidth, bar.y() + 4, bar.height() - 8);
        int vitalX = bar.x() + leftWidth + 5;
        int vitalGap = 3;
        int vitalWidth = Math.max(34, (vitalsWidth - 10 - vitalGap * 3) / 4);
        topMetric(graphics, font, vitalX, bar.y() + 4, vitalWidth, POPULATION_ICON, Component.literal("—"));
        topMetric(graphics, font, vitalX + (vitalWidth + vitalGap), bar.y() + 4, vitalWidth, HOUSING_ICON, Component.literal("—"));
        topMetric(graphics, font, vitalX + (vitalWidth + vitalGap) * 2, bar.y() + 4, vitalWidth, FOOD_ICON, Component.literal("—"));
        topMetric(graphics, font, vitalX + (vitalWidth + vitalGap) * 3, bar.y() + 4, vitalWidth, DEFENSE_ICON, Component.literal("—"));

        vDivider(graphics, resourcesX, bar.y() + 4, bar.height() - 8);
        if (resourcesWidth >= 110) {
            ItemStack[] resourceIcons = {WOOD_ICON, STONE_ICON, IRON_ICON, GOLD_ICON, REDSTONE_ICON, GRAIN_ICON};
            int count = resourcesWidth >= 280 ? resourceIcons.length : 3;
            int slotWidth = Math.max(32, (resourcesWidth - 8) / count);
            for (int i = 0; i < count; i++) {
                topMetric(graphics, font, resourcesX + 4 + i * slotWidth, bar.y() + 4,
                        slotWidth - 2, resourceIcons[i], Component.literal("—"));
            }
        }

        int actionX = bar.x() + bar.width() - rightButtonsWidth + 4;
        smallTopAction(graphics, actionX, bar.y() + 7, ALERT_ICON, false);
        smallTopAction(graphics, actionX + 24, bar.y() + 7, MINE_ICON, false);
        smallTopAction(graphics, actionX + 48, bar.y() + 7, MAP_ICON, false);
    }

    private static void renderAlerts(GuiGraphics graphics, Font font, HumanNpcEntity npc, int screenWidth) {
        List<Alert> alerts = new ArrayList<>();
        long hazardPlans = ExcavationOverlayState.plans().stream().filter(plan -> plan.hazardPaused()).count();
        if (hazardPlans > 0) {
            alerts.add(new Alert(HAZARD_ICON,
                    Component.translatable("hud.stonebanner.alert.excavation_hazard", hazardPlans), DANGER));
        }
        if (npc != null) {
            int health = percentage(npc.getHealth(), npc.getMaxHealth());
            if (npc.hudDanger() >= CitizenNeeds.CRITICAL_DANGER_THRESHOLD) {
                alerts.add(new Alert(DANGER_ICON, Component.translatable("hud.stonebanner.alert.danger"), DANGER));
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

        int count = Math.min(3, alerts.size());
        StoneBannerHudLayout.Rect bounds = StoneBannerHudLayout.alerts(screenWidth, count);
        panel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), false);
        for (int i = 0; i < count; i++) {
            Alert alert = alerts.get(i);
            int y = bounds.y() + 4 + i * 22;
            graphics.renderItem(alert.icon(), bounds.x() + 5, y + 2);
            String text = font.plainSubstrByWidth(alert.message().getString(), bounds.width() - 31);
            graphics.drawString(font, text, bounds.x() + 26, y + 6, alert.color(), true);
        }
    }

    private static void renderCitizenInspector(GuiGraphics graphics, Font font, HumanNpcEntity npc,
                                               int screenWidth, int screenHeight) {
        StoneBannerHudLayout.Rect card = StoneBannerHudLayout.citizenCard(screenWidth, screenHeight, citizenPanelExpanded);
        panel(graphics, card.x(), card.y(), card.width(), card.height(), true);

        int portraitSize = citizenPanelExpanded ? 54 : 30;
        inset(graphics, card.x() + 7, card.y() + 7, portraitSize, portraitSize, true);
        renderScaledItem(graphics, CITIZEN_ICON, card.x() + 12, card.y() + 12,
                citizenPanelExpanded ? 2.6F : 1.25F);

        int infoX = card.x() + portraitSize + 15;
        graphics.drawString(font, npc.getDisplayName().copy().withStyle(ChatFormatting.BOLD),
                infoX, card.y() + 8, TEXT, true);
        graphics.drawString(font,
                Component.translatable("profession.stonebanner." + npc.hudProfession().serializedName()),
                infoX, card.y() + 21, MUTED, false);

        WorkType workType = npc.hudWorkType();
        Component activity = workType == null
                ? Component.translatable("brain_state.stonebanner." + npc.brainState().serializedName())
                : Component.translatable("work_type.stonebanner." + workType.serializedName());
        graphics.drawString(font,
                font.plainSubstrByWidth(activity.getString(), Math.max(35, card.width() - infoX + card.x() - 30)),
                infoX, card.y() + 33, ACCENT, false);

        int toggleX = card.x() + card.width() - 27;
        inset(graphics, toggleX, card.y() + 5, 22, 22, true);
        graphics.drawCenteredString(font, citizenPanelExpanded ? "−" : "+", toggleX + 11, card.y() + 12, ACCENT);
        if (!citizenPanelExpanded) {
            return;
        }

        int barsY = card.y() + 67;
        int health = percentage(npc.getHealth(), npc.getMaxHealth());
        drawCompactBar(graphics, font, card.x() + 8, barsY, card.width() - 16,
                HEALTH_ICON, Component.translatable("hud.stonebanner.npc.health"), health, healthColor(health));
        drawCompactBar(graphics, font, card.x() + 8, barsY + 22, card.width() - 16,
                HUNGER_ICON, Component.translatable("hud.stonebanner.npc.hunger"), npc.hudHunger(),
                pressureColor(npc.hudHunger(), 60, 85));
        drawCompactBar(graphics, font, card.x() + 8, barsY + 44, card.width() - 16,
                FATIGUE_ICON, Component.translatable("hud.stonebanner.npc.fatigue"), npc.hudFatigue(),
                pressureColor(npc.hudFatigue(), 65, 90));

        int statY = barsY + 70;
        skillMetric(graphics, font, card.x() + 8, statY, 69, COMBAT_ICON, npc.hudSkill(CitizenSkill.COMBAT));
        skillMetric(graphics, font, card.x() + 81, statY, 69, CONSTRUCTION_ICON,
                npc.hudSkill(CitizenSkill.CONSTRUCTION));
        skillMetric(graphics, font, card.x() + 154, statY, card.width() - 162, MINING_ICON,
                npc.hudSkill(CitizenSkill.MINING));

        ItemStack[] tabIcons = {OVERVIEW_ICON, DETAILS_HEALTH_ICON, SKILLS_ICON, PRIORITIES_ICON, INVENTORY_ICON};
        for (int i = 0; i < tabIcons.length; i++) {
            StoneBannerHudLayout.Rect tab = StoneBannerHudLayout.citizenTab(screenWidth, screenHeight, true, i, tabIcons.length);
            inset(graphics, tab.x(), tab.y(), tab.width(), tab.height(), true);
            graphics.renderItem(tabIcons[i], tab.x() + Math.max(1, (tab.width() - 16) / 2), tab.y() + 6);
        }
    }

    private static void renderBottomDock(GuiGraphics graphics, Minecraft minecraft, int screenWidth,
                                         int screenHeight, HumanNpcEntity selectedNpc) {
        Font font = minecraft.font;
        StoneBannerHudLayout.Rect dock = StoneBannerHudLayout.bottomDock(screenWidth, screenHeight, bottomDockExpanded);
        panel(graphics, dock.x(), dock.y(), dock.width(), dock.height(), false);

        StoneBannerHudLayout.Rect toggle = StoneBannerHudLayout.bottomToggle(screenWidth, screenHeight, bottomDockExpanded);
        panel(graphics, toggle.x(), toggle.y(), toggle.width(), toggle.height(), true);
        graphics.drawCenteredString(font, bottomDockExpanded ? "⌄" : "⌃",
                toggle.x() + toggle.width() / 2, toggle.y() + 4, ACCENT);

        if (bottomDockExpanded) {
            BottomTab[] tabs = BottomTab.values();
            for (int i = 0; i < tabs.length; i++) {
                BottomTab tab = tabs[i];
                StoneBannerHudLayout.Rect bounds = StoneBannerHudLayout.bottomTab(screenWidth, screenHeight, i, tabs.length);
                toolbarButton(graphics, font, bounds, tab.icon(), Component.translatable(tab.translationKey()),
                        true, tab == activeBottomTab);
            }

            if (activeBottomTab == BottomTab.ORDERS) {
                renderOrdersRow(graphics, font, screenWidth, screenHeight);
            }
        }

        renderHotbar(graphics, minecraft, screenWidth, screenHeight);
    }

    private static void renderOrdersRow(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        DesignationType[] tools = orderTools();
        DesignationType activeType = DesignationController.activeType().orElse(null);
        boolean showAccessMode = activeType == DesignationType.EXCAVATE;
        boolean showExtend = activeType == DesignationType.TUNNEL || TunnelExtensionController.isActive();
        int slotCount = tools.length + ((showAccessMode || showExtend) ? 1 : 0);
        for (int i = 0; i < tools.length; i++) {
            DesignationType tool = tools[i];
            StoneBannerHudLayout.Rect bounds = StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, i, slotCount);
            toolButton(graphics, font, bounds, designationIcon(tool),
                    Component.translatable("designation.stonebanner." + tool.serializedName()), tool == activeType);
        }

        if (showAccessMode) {
            ExcavationAccessMode mode = DesignationController.excavationAccessMode();
            StoneBannerHudLayout.Rect accessBounds = StoneBannerHudLayout.bottomTool(
                    screenWidth, screenHeight, tools.length, slotCount
            );
            toolButton(
                    graphics,
                    font,
                    accessBounds,
                    ACCESS_ICON,
                    Component.translatable(
                            "hud.stonebanner.excavation.access",
                            Component.translatable("excavation_access.stonebanner." + mode.serializedName())
                    ),
                    true
            );
        } else if (showExtend) {
            StoneBannerHudLayout.Rect extendBounds = StoneBannerHudLayout.bottomTool(
                    screenWidth, screenHeight, tools.length, slotCount
            );
            toolButton(
                    graphics,
                    font,
                    extendBounds,
                    TUNNEL_ICON,
                    Component.translatable("hud.stonebanner.excavation.extend"),
                    TunnelExtensionController.isActive()
            );
        }

        StoneBannerHudLayout.Rect resourceEnd = StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, 2, slotCount);
        StoneBannerHudLayout.Rect excavationStart = StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, 3, slotCount);
        StoneBannerHudLayout.Rect excavationEnd = StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, 4, slotCount);
        StoneBannerHudLayout.Rect controlStart = StoneBannerHudLayout.bottomTool(screenWidth, screenHeight, 5, slotCount);
        int firstDivider = (resourceEnd.x() + resourceEnd.width() + excavationStart.x()) / 2;
        int secondDivider = (excavationEnd.x() + excavationEnd.width() + controlStart.x()) / 2;
        int dividerY = resourceEnd.y() + 3;
        int dividerHeight = Math.max(1, resourceEnd.height() - 6);
        vDivider(graphics, firstDivider, dividerY, dividerHeight);
        vDivider(graphics, secondDivider, dividerY, dividerHeight);
    }

    private static void renderHotbar(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        Font font = minecraft.font;
        int selected = minecraft.player == null ? -1 : minecraft.player.getInventory().selected;
        for (int i = 0; i < StoneBannerHudLayout.HOTBAR_SLOTS; i++) {
            StoneBannerHudLayout.Rect slot = StoneBannerHudLayout.hotbarSlot(
                    screenWidth, screenHeight, bottomDockExpanded, i
            );
            boolean active = i == selected;
            graphics.fill(slot.x(), slot.y(), slot.x() + slot.width(), slot.y() + slot.height(),
                    active ? SLOT_HOVER : SLOT);
            graphics.renderOutline(slot.x(), slot.y(), slot.width(), slot.height(), active ? BORDER_ACTIVE : FRAME);
            graphics.drawString(font, Integer.toString(i + 1), slot.x() + 3, slot.y() + 3,
                    active ? ACCENT : MUTED, false);

            if (minecraft.player == null) {
                continue;
            }
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int itemX = slot.x() + Math.max(2, (slot.width() - 16) / 2);
            int itemY = slot.y() + 9;
            graphics.renderItem(stack, itemX, itemY);
            if (stack.getCount() > 1) {
                String count = Integer.toString(stack.getCount());
                graphics.drawString(font, count,
                        slot.x() + slot.width() - font.width(count) - 2,
                        slot.y() + slot.height() - 10, TEXT, true);
            }
        }
    }

    private static void renderRightRail(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        Font font = minecraft.font;
        StoneBannerHudLayout.Rect rail = StoneBannerHudLayout.rightRail(screenWidth, screenHeight);
        panel(graphics, rail.x(), rail.y(), rail.width(), rail.height(), false);

        GameSpeedController.Speed speed = GameSpeedController.speed();
        String[] labels = {"II", ">", ">>", ">>>"};
        GameSpeedController.Speed[] speeds = {
                GameSpeedController.Speed.PAUSED,
                GameSpeedController.Speed.NORMAL,
                GameSpeedController.Speed.DOUBLE,
                GameSpeedController.Speed.TRIPLE
        };
        for (int i = 0; i < speeds.length; i++) {
            StoneBannerHudLayout.Rect button = StoneBannerHudLayout.timeButton(screenWidth, screenHeight, i);
            boolean active = speed == speeds[i];
            inset(graphics, button.x(), button.y(), button.width(), button.height(), true);
            if (active) {
                graphics.renderOutline(button.x() + 1, button.y() + 1, button.width() - 2, button.height() - 2,
                        BORDER_ACTIVE);
            }
            graphics.drawCenteredString(font, labels[i], button.x() + button.width() / 2,
                    button.y() + 10, active ? ACCENT : TEXT);
        }

        ItemStack[] layerIcons = {BANNER_ICON, MINE_ICON, ZONES_TAB_ICON};
        var layers = dev.stonebanner.client.control.MapLayerState.Layer.values();
        for (int i = 0; i < layers.length; i++) {
            var button = StoneBannerHudLayout.layerButton(screenWidth, screenHeight, i);
            inset(graphics, button.x(), button.y(), button.width(), button.height(), true);
            graphics.renderItem(layerIcons[i], button.x() + (button.width() - 16) / 2, button.y() + 4);
            if (dev.stonebanner.client.control.MapLayerState.enabled(layers[i]))
                graphics.renderOutline(button.x() + 1, button.y() + 1, button.width() - 2, button.height() - 2, BORDER_ACTIVE);
        }
        StoneBannerHudLayout.Rect clock = StoneBannerHudLayout.clockPanel(screenWidth, screenHeight);
        inset(graphics, clock.x(), clock.y(), clock.width(), clock.height(), true);
        graphics.renderItem(CLOCK_ICON, clock.x() + 8, clock.y() + 12);
        WorldClock worldClock = worldClock(minecraft);
        graphics.drawString(font, worldClock.time(), clock.x() + 31, clock.y() + 8, TEXT, true);
        graphics.drawString(font, Component.translatable("hud.stonebanner.top.day", worldClock.day()),
                clock.x() + 31, clock.y() + 22, MUTED, false);

        StoneBannerHudLayout.Rect map = StoneBannerHudLayout.miniMap(screenWidth, screenHeight);
        inset(graphics, map.x(), map.y(), map.width(), map.height(), true);
        renderMiniMap(graphics, minecraft, map);
    }

    private static void renderMiniMap(GuiGraphics graphics, Minecraft minecraft, StoneBannerHudLayout.Rect bounds) {
        if (minecraft.player == null || minecraft.level == null) {
            graphics.renderItem(MAP_ICON, bounds.x() + 6, bounds.y() + 6);
            return;
        }
        MINI_MAP.refreshIfNeeded(minecraft);
        int size = MiniMapCache.SIZE;
        int innerWidth = Math.max(1, bounds.width() - 8);
        int innerHeight = Math.max(1, bounds.height() - 8);
        int cell = Math.max(1, Math.min(innerWidth / size, innerHeight / size));
        int mapWidth = cell * size;
        int mapHeight = cell * size;
        int startX = bounds.x() + (bounds.width() - mapWidth) / 2;
        int startY = bounds.y() + (bounds.height() - mapHeight) / 2;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int color = MINI_MAP.colorAt(x, z);
                int px = startX + x * cell;
                int py = startY + z * cell;
                graphics.fill(px, py, px + cell, py + cell, color);
            }
        }
        int centerX = startX + (size / 2) * cell;
        int centerY = startY + (size / 2) * cell;
        graphics.renderOutline(centerX - 2, centerY - 2, Math.max(4, cell + 3), Math.max(4, cell + 3), TEXT);
        graphics.drawString(minecraft.font, "N", startX + mapWidth / 2 - 2, startY + 2, TEXT, true);
    }

    private static void topMetric(GuiGraphics graphics, Font font, int x, int y, int width,
                                  ItemStack icon, Component value) {
        if (width <= 12) {
            return;
        }
        graphics.renderItem(icon, x + Math.max(1, (width - 16) / 2), y + 1);
        String text = font.plainSubstrByWidth(value.getString(), width - 2);
        graphics.drawCenteredString(font, text, x + width / 2, y + 22, TEXT);
    }

    private static void smallTopAction(GuiGraphics graphics, int x, int y, ItemStack icon, boolean active) {
        inset(graphics, x, y, 21, 28, true);
        if (active) {
            graphics.renderOutline(x + 1, y + 1, 19, 26, BORDER_ACTIVE);
        }
        graphics.renderItem(icon, x + 3, y + 6);
    }

    private static void drawCompactBar(GuiGraphics graphics, Font font, int x, int y, int width, ItemStack icon,
                                       Component label, int percent, int fillColor) {
        int clamped = Math.max(0, Math.min(100, percent));
        graphics.renderItem(icon, x, y);
        int contentX = x + 21;
        int contentWidth = width - 21;
        graphics.drawString(font, label, contentX, y + 1, MUTED, false);
        Component value = Component.literal(clamped + "%");
        graphics.drawString(font, value, contentX + contentWidth - font.width(value), y + 1, TEXT, true);
        int top = y + 13;
        graphics.fill(contentX, top, contentX + contentWidth, top + 5, BAR_TRACK);
        graphics.hLine(contentX + 1, contentX + contentWidth - 2, top, BAR_HIGHLIGHT);
        int fillWidth = Math.round(contentWidth * (clamped / 100.0F));
        if (fillWidth > 0) {
            graphics.fill(contentX + 1, top + 1, contentX + fillWidth, top + 4, fillColor);
        }
    }

    private static void skillMetric(GuiGraphics graphics, Font font, int x, int y, int width,
                                    ItemStack icon, int value) {
        if (width <= 20) {
            return;
        }
        inset(graphics, x, y, width, 25, true);
        graphics.renderItem(icon, x + 3, y + 4);
        graphics.drawString(font, Integer.toString(value), x + 22, y + 9, TEXT, true);
    }

    private static void toolbarButton(GuiGraphics graphics, Font font, StoneBannerHudLayout.Rect bounds,
                                      ItemStack icon, Component label, boolean enabled, boolean active) {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                active ? SLOT_HOVER : enabled ? SLOT : SLOT_DISABLED);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                active ? BORDER_ACTIVE : FRAME);
        graphics.renderItem(icon, bounds.x() + 7, bounds.y() + 9);
        drawScaledString(graphics, font, label, bounds.x() + 27, bounds.y() + 13,
                Math.max(1, bounds.width() - 29), enabled ? (active ? ACCENT : TEXT) : 0xFF77736C);
    }

    private static void toolButton(GuiGraphics graphics, Font font, StoneBannerHudLayout.Rect bounds,
                                   ItemStack icon, Component label, boolean active) {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                active ? SLOT_HOVER : SLOT);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                active ? BORDER_ACTIVE : FRAME);
        graphics.renderItem(icon, bounds.x() + Math.max(2, (bounds.width() - 16) / 2), bounds.y() + 3);
        drawCenteredScaledString(graphics, font, label, bounds.x() + bounds.width() / 2,
                bounds.y() + Math.max(19, bounds.height() - 11), Math.max(12, bounds.width() - 6),
                active ? ACCENT : TEXT);
    }

    private static void renderScaledItem(GuiGraphics graphics, ItemStack item, int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(item, 0, 0);
        graphics.pose().popPose();
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, Component text, int x, int y,
                                         int maxWidth, int color) {
        int width = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, maxWidth / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawCenteredScaledString(GuiGraphics graphics, Font font, Component text,
                                                  int centerX, int y, int maxWidth, int color) {
        int width = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, maxWidth / (float) width);
        float scaledWidth = width * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - scaledWidth / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, PANEL_SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL_OUTER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL_INNER);
        graphics.hLine(x + 1, x + width - 2, y + 1, FRAME_LIGHT);
        graphics.vLine(x + 1, y + 1, y + height - 2, FRAME_LIGHT);
        graphics.hLine(x + 1, x + width - 1, y + height - 1, FRAME_DARK);
        graphics.vLine(x + width - 1, y + 1, y + height - 1, FRAME_DARK);
        graphics.renderOutline(x + 2, y + 2, Math.max(1, width - 4), Math.max(1, height - 4),
                active ? BORDER_ACTIVE : FRAME);
    }

    private static void inset(GuiGraphics graphics, int x, int y, int width, int height, boolean enabled) {
        graphics.fill(x, y, x + width, y + height, enabled ? SLOT : SLOT_DISABLED);
        graphics.renderOutline(x, y, width, height, enabled ? FRAME : FRAME_DARK);
    }

    private static void vDivider(GuiGraphics graphics, int x, int y, int height) {
        graphics.vLine(x, y, y + height, FRAME_DARK);
        graphics.vLine(x + 1, y, y + height, FRAME_LIGHT);
    }

    private static DesignationType[] orderTools() {
        return new DesignationType[]{
                DesignationType.CHOP,
                DesignationType.MINE,
                DesignationType.CLEAR,
                DesignationType.EXCAVATE,
                DesignationType.TUNNEL,
                DesignationType.CANCEL
        };
    }

    private static HudAction designationAction(DesignationType type) {
        return switch (type) {
            case CHOP -> HudAction.DESIGNATE_CHOP;
            case MINE -> HudAction.DESIGNATE_MINE;
            case EXCAVATE -> HudAction.DESIGNATE_EXCAVATE;
            case TUNNEL -> HudAction.DESIGNATE_TUNNEL;
            case CLEAR -> HudAction.DESIGNATE_CLEAR;
            case CANCEL -> HudAction.DESIGNATE_CANCEL;
        };
    }

    private static HudAction hotbarAction(int index) {
        return switch (index) {
            case 0 -> HudAction.HOTBAR_1;
            case 1 -> HudAction.HOTBAR_2;
            case 2 -> HudAction.HOTBAR_3;
            case 3 -> HudAction.HOTBAR_4;
            case 4 -> HudAction.HOTBAR_5;
            case 5 -> HudAction.HOTBAR_6;
            case 6 -> HudAction.HOTBAR_7;
            case 7 -> HudAction.HOTBAR_8;
            default -> HudAction.HOTBAR_9;
        };
    }

    private static ItemStack designationIcon(DesignationType type) {
        return switch (type) {
            case CHOP -> CHOP_ICON;
            case MINE -> MINE_ICON;
            case EXCAVATE -> EXCAVATE_ICON;
            case TUNNEL -> TUNNEL_ICON;
            case CLEAR -> CLEAR_ICON;
            case CANCEL -> CANCEL_ICON;
        };
    }

    private static WorldClock worldClock(Minecraft minecraft) {
        if (minecraft.level == null) {
            return new WorldClock(1, "--:--", Component.translatable("hud.stonebanner.weather.clear"), WEATHER_CLEAR_ICON);
        }
        long dayTime = minecraft.level.getDayTime();
        long day = Math.floorDiv(dayTime, 24000L) + 1L;
        long ticks = Math.floorMod(dayTime, 24000L);
        int totalMinutes = (int) (((ticks + 6000L) % 24000L) * 1440L / 24000L);
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        String time = String.format("%02d:%02d", hour, minute);
        if (minecraft.level.isThundering()) {
            return new WorldClock(day, time, Component.translatable("hud.stonebanner.weather.thunder"), WEATHER_RAIN_ICON);
        }
        if (minecraft.level.isRaining()) {
            return new WorldClock(day, time, Component.translatable("hud.stonebanner.weather.rain"), WEATHER_RAIN_ICON);
        }
        return new WorldClock(day, time, Component.translatable("hud.stonebanner.weather.clear"), WEATHER_CLEAR_ICON);
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
        CONSUME,
        TAB_BUILD,
        TAB_ORDERS,
        TAB_ZONES,
        TAB_RESEARCH,
        TAB_CRAFTING,
        TOGGLE_BOTTOM_DOCK,
        TOGGLE_CITIZEN,
        OPEN_CITIZEN_OVERVIEW,
        OPEN_CITIZEN_HEALTH,
        OPEN_CITIZEN_SKILLS,
        OPEN_WORK,
        OPEN_CITIZEN_INVENTORY,
        DESIGNATE_CHOP,
        DESIGNATE_MINE,
        DESIGNATE_EXCAVATE,
        DESIGNATE_TUNNEL,
        DESIGNATE_CLEAR,
        DESIGNATE_CANCEL,
        CYCLE_EXCAVATION_ACCESS,
        EXTEND_TUNNEL,
        HOTBAR_1,
        HOTBAR_2,
        HOTBAR_3,
        HOTBAR_4,
        HOTBAR_5,
        HOTBAR_6,
        HOTBAR_7,
        HOTBAR_8,
        HOTBAR_9,
        LAYER_BOUNDARIES,
        LAYER_RESOURCES,
        LAYER_FERTILITY,
        TIME_PAUSE,
        TIME_NORMAL,
        TIME_DOUBLE,
        TIME_TRIPLE
    }

    private enum BottomTab {
        BUILD("hud.stonebanner.menu.build", BUILD_TAB_ICON),
        ORDERS("hud.stonebanner.menu.orders", ORDERS_TAB_ICON),
        ZONES("hud.stonebanner.menu.zones", ZONES_TAB_ICON),
        RESEARCH("hud.stonebanner.menu.research", RESEARCH_TAB_ICON),
        CRAFTING("hud.stonebanner.menu.crafting", CRAFTING_TAB_ICON);

        private final String translationKey;
        private final ItemStack icon;

        BottomTab(String translationKey, ItemStack icon) {
            this.translationKey = translationKey;
            this.icon = icon;
        }

        public String translationKey() {
            return translationKey;
        }

        public ItemStack icon() {
            return icon;
        }
    }

    private record Alert(ItemStack icon, Component message, int color) { }
    private record WorldClock(long day, String time, Component weatherLabel, ItemStack weatherIcon) { }

    private static final class MiniMapCache {
        private static final int RADIUS = 7;
        private static final int SIZE = RADIUS * 2 + 1;
        private static final int SAMPLE_STEP = 4;
        private static final long REFRESH_TICKS = 20L;

        private final int[] colors = new int[SIZE * SIZE];
        private long lastRefresh = Long.MIN_VALUE;
        private int centerX = Integer.MIN_VALUE;
        private int centerY;
        private int centerZ = Integer.MIN_VALUE;

        private MiniMapCache() {
            for (int i = 0; i < colors.length; i++) colors[i] = MAP_LEVEL;
        }

        private void refreshIfNeeded(Minecraft minecraft) {
            if (minecraft.player == null || minecraft.level == null) return;
            BlockPos center = minecraft.player.blockPosition();
            long gameTime = minecraft.level.getGameTime();
            boolean moved = Math.abs(center.getX() - centerX) >= SAMPLE_STEP
                    || Math.abs(center.getZ() - centerZ) >= SAMPLE_STEP;
            if (!moved && gameTime - lastRefresh < REFRESH_TICKS) return;

            centerX = center.getX();
            centerY = center.getY();
            centerZ = center.getZ();
            lastRefresh = gameTime;

            for (int gridZ = 0; gridZ < SIZE; gridZ++) {
                for (int gridX = 0; gridX < SIZE; gridX++) {
                    int worldX = centerX + (gridX - RADIUS) * SAMPLE_STEP;
                    int worldZ = centerZ + (gridZ - RADIUS) * SAMPLE_STEP;
                    int surfaceY = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1;
                    surfaceY = Math.max(minecraft.level.getMinBuildHeight(), surfaceY);
                    BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
                    int color;
                    if (!minecraft.level.getFluidState(surface).isEmpty()) {
                        color = MAP_WATER;
                    } else if (minecraft.level.getBlockState(surface).is(Blocks.SAND)
                            || minecraft.level.getBlockState(surface).is(Blocks.RED_SAND)) {
                        color = MAP_SAND;
                    } else if (minecraft.level.getBlockState(surface).is(Blocks.SNOW_BLOCK)
                            || minecraft.level.getBlockState(surface).is(Blocks.SNOW)) {
                        color = MAP_SNOW;
                    } else if (surfaceY > centerY + 8) {
                        color = MAP_HIGH;
                    } else if (surfaceY < centerY - 8) {
                        color = MAP_LOW;
                    } else {
                        color = MAP_LEVEL;
                    }
                    colors[gridZ * SIZE + gridX] = color;
                }
            }
        }

        private int colorAt(int x, int z) {
            if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return MAP_LEVEL;
            return colors[z * SIZE + x];
        }
    }
}
