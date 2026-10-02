package dev.stonebanner.client.screen;

import dev.stonebanner.citizen.BodyPart;
import dev.stonebanner.citizen.CitizenHudCodec;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.InjuryState;
import dev.stonebanner.citizen.WorkPriority;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.client.control.CitizenInventoryClientCache;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** Full Citizen inspector. Work tab doubles as the RimWorld-style nearby work-priority matrix. */
public final class CitizenDetailsScreen extends Screen {
    private static final int PANEL = 0xEE111418;
    private static final int PANEL_SOFT = 0xDD1B1F24;
    private static final int BORDER = 0xFF6E6658;
    private static final int ACCENT = 0xFFE0B66A;
    private static final int TEXT = 0xFFF0ECE3;
    private static final int MUTED = 0xFFAAA49A;
    private static final int GOOD = 0xFF79C979;
    private static final int WARNING = 0xFFE2B85C;
    private static final int DANGER = 0xFFE76F6F;

    private static final int PANEL_WIDTH = 680;
    private static final int PANEL_HEIGHT = 360;
    private static final int TAB_HEIGHT = 22;
    private static final int NAME_COLUMN_WIDTH = 118;
    private static final int WORK_CELL_WIDTH = 42;
    private static final int WORK_ROW_HEIGHT = 20;
    private static final double WORK_TABLE_RANGE = 48.0D;
    private static final int INVENTORY_COLUMNS = 3;
    private static final int INVENTORY_SLOT_SIZE = 26;
    private static final int INVENTORY_REFRESH_TICKS = 20;

    private final Screen parent;
    private final int citizenEntityId;
    private Tab activeTab;
    private int inventoryRefreshTicks;

    public CitizenDetailsScreen(Screen parent, int citizenEntityId, Tab initialTab) {
        super(Component.translatable("screen.stonebanner.citizen"));
        this.parent = parent;
        this.citizenEntityId = citizenEntityId;
        this.activeTab = initialTab == null ? Tab.OVERVIEW : initialTab;
    }

    @Override
    protected void init() {
        super.init();
        if (activeTab == Tab.INVENTORY) {
            requestInventorySnapshot(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (activeTab != Tab.INVENTORY) {
            inventoryRefreshTicks = 0;
            return;
        }
        if (++inventoryRefreshTicks >= INVENTORY_REFRESH_TICKS) {
            requestInventorySnapshot(false);
        }
    }

    @Override
    public void onClose() {
        CitizenInventoryClientCache.clear(citizenEntityId);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);

        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.renderOutline(x, y, panelWidth, panelHeight, BORDER);

        HumanNpcEntity citizen = citizen();
        if (citizen == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.stonebanner.citizen.missing"),
                    width / 2, height / 2, DANGER);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(font, citizen.getDisplayName().copy().withStyle(ChatFormatting.BOLD), x + 12, y + 11, TEXT);
        graphics.drawString(font,
                Component.translatable("profession.stonebanner." + citizen.hudProfession().serializedName()),
                x + 12, y + 23, MUTED);

        renderTabs(graphics, x + 10, y + 40, panelWidth - 20, mouseX, mouseY);
        int contentY = y + 40 + TAB_HEIGHT + 12;
        switch (activeTab) {
            case OVERVIEW -> renderOverview(graphics, citizen, x + 16, contentY);
            case HEALTH -> renderHealth(graphics, citizen, x + 16, contentY);
            case SKILLS -> renderSkills(graphics, citizen, x + 16, contentY);
            case WORK -> renderWork(graphics, citizen, x + 10, contentY, panelWidth - 20, panelHeight - 88, mouseX, mouseY);
            case INVENTORY -> renderInventory(graphics, x + 16, contentY, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics graphics, int x, int y, int availableWidth, int mouseX, int mouseY) {
        int tabWidth = Math.max(74, availableWidth / Tab.values().length);
        int cursor = x;
        for (Tab tab : Tab.values()) {
            int widthForTab = Math.min(tabWidth, x + availableWidth - cursor);
            boolean active = tab == activeTab;
            boolean hovered = inside(mouseX, mouseY, cursor, y, widthForTab, TAB_HEIGHT);
            graphics.fill(cursor, y, cursor + widthForTab, y + TAB_HEIGHT,
                    active ? 0xDD373027 : hovered ? 0xCC2A2E33 : PANEL_SOFT);
            graphics.renderOutline(cursor, y, widthForTab, TAB_HEIGHT, active ? ACCENT : BORDER);
            Component label = Component.translatable("screen.stonebanner.citizen.tab." + tab.serializedName);
            graphics.drawCenteredString(font, label, cursor + widthForTab / 2, y + 7, active ? ACCENT : TEXT);
            cursor += widthForTab;
        }
    }

    private void renderOverview(GuiGraphics graphics, HumanNpcEntity citizen, int x, int y) {
        int line = y;
        line = keyValue(graphics, x, line, "screen.stonebanner.citizen.state",
                Component.translatable("brain_state.stonebanner." + citizen.brainState().serializedName()));
        WorkType work = citizen.hudWorkType();
        line = keyValue(graphics, x, line, "screen.stonebanner.citizen.current_work",
                work == null ? Component.translatable("screen.stonebanner.none")
                        : Component.translatable("work_type.stonebanner." + work.serializedName()));
        line = keyValue(graphics, x, line, "hud.stonebanner.npc.health",
                Component.literal(Math.round(citizen.getHealth() / citizen.getMaxHealth() * 100.0F) + "%"));
        line = keyValue(graphics, x, line, "hud.stonebanner.npc.hunger", Component.literal(citizen.hudHunger() + "%"));
        line = keyValue(graphics, x, line, "hud.stonebanner.npc.fatigue", Component.literal(citizen.hudFatigue() + "%"));
        keyValue(graphics, x, line, "hud.stonebanner.npc.danger", Component.literal(citizen.hudDanger() + "%"));
    }

    private void renderHealth(GuiGraphics graphics, HumanNpcEntity citizen, int x, int y) {
        int line = y;
        for (BodyPart part : BodyPart.values()) {
            InjuryState injury = citizen.hudInjury(part);
            Component label = Component.translatable("body_part.stonebanner." + part.serializedName());
            Component value = Component.translatable("injury.stonebanner." + injury.serializedName());
            graphics.drawString(font, label, x, line, TEXT);
            graphics.drawString(font, value, x + 150, line, injuryColor(injury));
            line += 21;
        }
    }

    private void renderSkills(GuiGraphics graphics, HumanNpcEntity citizen, int x, int y) {
        int line = y;
        for (CitizenSkill skill : CitizenSkill.values()) {
            Component label = Component.translatable("skill.stonebanner." + skill.serializedName());
            int value = citizen.hudSkill(skill);
            graphics.drawString(font, label, x, line, TEXT);
            graphics.drawString(font, Component.literal(Integer.toString(value)), x + 150, line, skillColor(value));
            drawMiniBar(graphics, x + 180, line + 2, 140, value, 10);
            line += 25;
        }
    }

    private void renderWork(GuiGraphics graphics, HumanNpcEntity selected, int x, int y,
                            int availableWidth, int availableHeight, int mouseX, int mouseY) {
        List<HumanNpcEntity> citizens = nearbyCitizens(selected);
        WorkType[] workTypes = WorkType.values();
        int requiredWidth = NAME_COLUMN_WIDTH + workTypes.length * WORK_CELL_WIDTH;
        int tableWidth = Math.min(availableWidth, requiredWidth);

        graphics.drawString(font, Component.translatable("screen.stonebanner.work.hint"), x + 2, y, MUTED);
        int headerY = y + 20;
        graphics.fill(x, headerY, x + tableWidth, headerY + WORK_ROW_HEIGHT, PANEL_SOFT);
        graphics.drawString(font, Component.translatable("screen.stonebanner.work.citizen"),
                x + 5, headerY + 6, MUTED);

        for (int column = 0; column < workTypes.length; column++) {
            int cellX = x + NAME_COLUMN_WIDTH + column * WORK_CELL_WIDTH;
            if (cellX + WORK_CELL_WIDTH > x + tableWidth) {
                break;
            }
            Component label = Component.translatable("work_short.stonebanner." + workTypes[column].serializedName());
            graphics.drawCenteredString(font, label, cellX + WORK_CELL_WIDTH / 2, headerY + 6, MUTED);
        }

        int maxRows = Math.max(1, (availableHeight - 48) / WORK_ROW_HEIGHT);
        int rows = Math.min(maxRows, citizens.size());
        for (int row = 0; row < rows; row++) {
            HumanNpcEntity citizen = citizens.get(row);
            int rowY = headerY + WORK_ROW_HEIGHT + row * WORK_ROW_HEIGHT;
            boolean selectedRow = citizen.getId() == selected.getId();
            graphics.fill(x, rowY, x + tableWidth, rowY + WORK_ROW_HEIGHT,
                    selectedRow ? 0x77372F25 : (row % 2 == 0 ? 0x66171A1E : 0x5521262B));
            graphics.drawString(font, citizen.getDisplayName(), x + 5, rowY + 6, selectedRow ? ACCENT : TEXT);

            for (int column = 0; column < workTypes.length; column++) {
                int cellX = x + NAME_COLUMN_WIDTH + column * WORK_CELL_WIDTH;
                if (cellX + WORK_CELL_WIDTH > x + tableWidth) {
                    break;
                }
                WorkPriority priority = citizen.hudWorkPriority(workTypes[column]);
                boolean hovered = inside(mouseX, mouseY, cellX, rowY, WORK_CELL_WIDTH, WORK_ROW_HEIGHT);
                if (hovered) {
                    graphics.fill(cellX + 1, rowY + 1, cellX + WORK_CELL_WIDTH - 1, rowY + WORK_ROW_HEIGHT - 1,
                            0x884D463B);
                }
                graphics.renderOutline(cellX, rowY, WORK_CELL_WIDTH, WORK_ROW_HEIGHT, 0x554F4A43);
                graphics.drawCenteredString(font, priorityLabel(priority),
                        cellX + WORK_CELL_WIDTH / 2, rowY + 6, priorityColor(priority));
            }
        }
    }

    private void renderInventory(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        List<ItemStack> stacks = CitizenInventoryClientCache.snapshot(citizenEntityId);
        if (stacks.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.stonebanner.inventory.loading"), x, y, MUTED);
            return;
        }

        graphics.drawString(font, Component.translatable("screen.stonebanner.inventory.personal"), x, y, TEXT);
        int gridY = y + 22;
        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < stacks.size(); slot++) {
            int column = slot % INVENTORY_COLUMNS;
            int row = slot / INVENTORY_COLUMNS;
            int slotX = x + column * INVENTORY_SLOT_SIZE;
            int slotY = gridY + row * INVENTORY_SLOT_SIZE;
            graphics.fill(slotX, slotY, slotX + 22, slotY + 22, PANEL_SOFT);
            graphics.renderOutline(slotX, slotY, 22, 22, BORDER);

            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX + 3, slotY + 3);
                graphics.renderItemDecorations(font, stack, slotX + 3, slotY + 3);
                if (inside(mouseX, mouseY, slotX, slotY, 22, 22)) {
                    hovered = stack;
                }
            }
        }

        graphics.drawString(font, Component.translatable("screen.stonebanner.inventory.real_items"),
                x + 104, gridY + 2, MUTED);
        graphics.drawString(font, Component.translatable("screen.stonebanner.inventory.food_hint"),
                x + 104, gridY + 20, MUTED);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;

        int tabX = x + 10;
        int tabY = y + 40;
        int availableWidth = panelWidth - 20;
        int tabWidth = Math.max(74, availableWidth / Tab.values().length);
        int cursor = tabX;
        for (Tab tab : Tab.values()) {
            int widthForTab = Math.min(tabWidth, tabX + availableWidth - cursor);
            if (inside(mouseX, mouseY, cursor, tabY, widthForTab, TAB_HEIGHT)) {
                if (activeTab != tab) {
                    activeTab = tab;
                    if (tab == Tab.INVENTORY) {
                        requestInventorySnapshot(true);
                    }
                }
                return true;
            }
            cursor += widthForTab;
        }

        if (activeTab == Tab.WORK) {
            HumanNpcEntity selected = citizen();
            if (selected != null && handleWorkClick(selected, x + 10, y + 40 + TAB_HEIGHT + 12,
                    panelWidth - 20, panelHeight - 88, mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void requestInventorySnapshot(boolean clearOld) {
        if (clearOld) {
            CitizenInventoryClientCache.clear(citizenEntityId);
        }
        StoneBannerNetwork.requestCitizenInventory(citizenEntityId);
        inventoryRefreshTicks = 0;
    }

    private boolean handleWorkClick(HumanNpcEntity selected, int x, int y, int availableWidth, int availableHeight,
                                    double mouseX, double mouseY) {
        List<HumanNpcEntity> citizens = nearbyCitizens(selected);
        WorkType[] workTypes = WorkType.values();
        int tableWidth = Math.min(availableWidth, NAME_COLUMN_WIDTH + workTypes.length * WORK_CELL_WIDTH);
        int headerY = y + 20;
        int maxRows = Math.max(1, (availableHeight - 48) / WORK_ROW_HEIGHT);
        int rows = Math.min(maxRows, citizens.size());

        for (int row = 0; row < rows; row++) {
            int rowY = headerY + WORK_ROW_HEIGHT + row * WORK_ROW_HEIGHT;
            for (int column = 0; column < workTypes.length; column++) {
                int cellX = x + NAME_COLUMN_WIDTH + column * WORK_CELL_WIDTH;
                if (cellX + WORK_CELL_WIDTH > x + tableWidth) {
                    break;
                }
                if (!inside(mouseX, mouseY, cellX, rowY, WORK_CELL_WIDTH, WORK_ROW_HEIGHT)) {
                    continue;
                }

                HumanNpcEntity citizen = citizens.get(row);
                WorkType workType = workTypes[column];
                WorkPriority next = CitizenHudCodec.nextPriority(citizen.hudWorkPriority(workType));
                StoneBannerNetwork.sendWorkPriority(
                        citizen.getId(),
                        workType.ordinal(),
                        CitizenHudCodec.encodePriority(next)
                );
                return true;
            }
        }
        return false;
    }

    private List<HumanNpcEntity> nearbyCitizens(HumanNpcEntity selected) {
        if (minecraft.level == null) {
            return List.of(selected);
        }
        List<HumanNpcEntity> result = minecraft.level.getEntitiesOfClass(
                HumanNpcEntity.class,
                selected.getBoundingBox().inflate(WORK_TABLE_RANGE),
                HumanNpcEntity::isAlive
        );
        result.sort(Comparator
                .comparing((HumanNpcEntity npc) -> npc.getId() == selected.getId() ? 0 : 1)
                .thenComparing(npc -> npc.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(HumanNpcEntity::getId));
        return result;
    }

    private HumanNpcEntity citizen() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(citizenEntityId) instanceof HumanNpcEntity citizen ? citizen : null;
    }

    private int keyValue(GuiGraphics graphics, int x, int y, String key, Component value) {
        graphics.drawString(font, Component.translatable(key), x, y, MUTED);
        graphics.drawString(font, value, x + 155, y, TEXT);
        return y + 22;
    }

    private void drawMiniBar(GuiGraphics graphics, int x, int y, int width, int value, int max) {
        graphics.fill(x, y, x + width, y + 5, 0xFF282C31);
        int fill = Math.round(width * (Math.max(0, Math.min(max, value)) / (float) max));
        graphics.fill(x, y, x + fill, y + 5, ACCENT);
    }

    private static Component priorityLabel(WorkPriority priority) {
        return priority == WorkPriority.DISABLED
                ? Component.literal("X")
                : Component.literal(Integer.toString(priority.code()));
    }

    private static int priorityColor(WorkPriority priority) {
        return switch (priority) {
            case CRITICAL -> 0xFFFFD166;
            case HIGH -> GOOD;
            case NORMAL -> TEXT;
            case LOW -> MUTED;
            case DISABLED -> DANGER;
        };
    }

    private static int injuryColor(InjuryState injury) {
        return switch (injury) {
            case NORMAL -> GOOD;
            case WOUNDED -> WARNING;
            case HEAVY_WOUND, FRACTURE, MISSING -> DANGER;
        };
    }

    private static int skillColor(int value) {
        if (value >= 8) {
            return GOOD;
        }
        if (value >= 5) {
            return ACCENT;
        }
        return TEXT;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public enum Tab {
        OVERVIEW("overview"),
        HEALTH("health"),
        SKILLS("skills"),
        WORK("work"),
        INVENTORY("inventory");

        private final String serializedName;

        Tab(String serializedName) {
            this.serializedName = serializedName;
        }
    }
}
