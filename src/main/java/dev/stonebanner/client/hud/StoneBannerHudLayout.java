package dev.stonebanner.client.hud;

/**
 * Pure layout calculations for the Stone & Banner HUD.
 *
 * <p>The centre of the game view is deliberately protected: strategic information stays on the top edge,
 * the selected Citizen lives in the lower-left corner, simulation controls/minimap live in the lower-right,
 * and the command dock uses only the remaining lower-centre space. The player's real 1-9 hotbar is always
 * the lowest row; command tabs and their contextual tools can collapse above it.</p>
 */
public final class StoneBannerHudLayout {
    public static final int SCREEN_MARGIN = 6;
    public static final int TOP_HEIGHT = 44;

    public static final int CITIZEN_WIDTH = 235;
    public static final int CITIZEN_EXPANDED_HEIGHT = 226;
    public static final int CITIZEN_COLLAPSED_HEIGHT = 42;
    public static final int CITIZEN_TAB_HEIGHT = 28;

    public static final int RIGHT_RAIL_WIDTH = 178;
    public static final int RIGHT_RAIL_HEIGHT = 212;
    public static final int TIME_BUTTON_HEIGHT = 28;

    public static final int BOTTOM_DOCK_MAX_WIDTH = 680;
    public static final int BOTTOM_DOCK_MIN_WIDTH = 360;
    public static final int BOTTOM_DOCK_EXPANDED_HEIGHT = 142;
    public static final int BOTTOM_DOCK_COLLAPSED_HEIGHT = 42;
    public static final int BOTTOM_TAB_HEIGHT = 34;
    public static final int BOTTOM_TOGGLE_WIDTH = 48;
    public static final int BOTTOM_TOGGLE_HEIGHT = 15;
    public static final int HOTBAR_HEIGHT = 34;
    public static final int HOTBAR_SLOTS = 9;

    public static final int ALERT_WIDTH = 250;

    private StoneBannerHudLayout() {
    }

    public static Rect topBar(int screenWidth) {
        return new Rect(SCREEN_MARGIN, SCREEN_MARGIN, Math.max(0, screenWidth - SCREEN_MARGIN * 2), TOP_HEIGHT);
    }

    public static Rect citizenCard(int screenWidth, int screenHeight, boolean expanded) {
        int height = expanded ? CITIZEN_EXPANDED_HEIGHT : CITIZEN_COLLAPSED_HEIGHT;
        int width = Math.min(CITIZEN_WIDTH, Math.max(1, screenWidth - SCREEN_MARGIN * 2));
        return new Rect(SCREEN_MARGIN, Math.max(TOP_HEIGHT + 16, screenHeight - height - SCREEN_MARGIN), width, height);
    }

    /** Group overview replaces the single-citizen inspector rather than opening one at random. */
    public static Rect groupCard(int screenWidth, int screenHeight) {
        int height = 70;
        int width = Math.min(CITIZEN_WIDTH, Math.max(1, screenWidth - SCREEN_MARGIN * 2));
        return new Rect(SCREEN_MARGIN, Math.max(TOP_HEIGHT + 16, screenHeight - height - SCREEN_MARGIN), width, height);
    }

    public static Rect citizenTab(int screenWidth, int screenHeight, boolean expanded, int index, int count) {
        if (!expanded || count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Citizen tab index must be inside an expanded card");
        }
        Rect card = citizenCard(screenWidth, screenHeight, true);
        int gap = 2;
        int available = card.width() - 8 - gap * (count - 1);
        int width = Math.max(1, available / count);
        int used = width * count + gap * (count - 1);
        int start = card.x() + (card.width() - used) / 2;
        return new Rect(start + index * (width + gap), card.y() + card.height() - CITIZEN_TAB_HEIGHT - 4,
                width, CITIZEN_TAB_HEIGHT);
    }

    public static Rect rightRail(int screenWidth, int screenHeight) {
        int width = Math.min(RIGHT_RAIL_WIDTH, Math.max(1, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(RIGHT_RAIL_HEIGHT, Math.max(1, screenHeight - TOP_HEIGHT - 20));
        return new Rect(Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN),
                Math.max(TOP_HEIGHT + 12, screenHeight - height - SCREEN_MARGIN), width, height);
    }

    public static Rect timeButton(int screenWidth, int screenHeight, int index) {
        if (index < 0 || index >= 4) {
            throw new IllegalArgumentException("Time button index must be between 0 and 3");
        }
        Rect rail = rightRail(screenWidth, screenHeight);
        int gap = 3;
        int width = (rail.width() - 10 - gap * 3) / 4;
        return new Rect(rail.x() + 5 + index * (width + gap), rail.y() + 5, width, TIME_BUTTON_HEIGHT);
    }

    public static Rect layerButton(int screenWidth, int screenHeight, int index) {
        if (index < 0 || index >= 3) throw new IllegalArgumentException("Layer index");
        Rect rail = rightRail(screenWidth, screenHeight);
        int gap = 3, width = (rail.width() - 10 - gap * 2) / 3;
        return new Rect(rail.x() + 5 + index * (width + gap), rail.y() + 38, width, 24);
    }
    public static Rect clockPanel(int screenWidth, int screenHeight) {
        Rect rail = rightRail(screenWidth, screenHeight);
        return new Rect(rail.x() + 5, rail.y() + 68, rail.width() - 10, 42);
    }

    public static Rect miniMap(int screenWidth, int screenHeight) {
        Rect rail = rightRail(screenWidth, screenHeight);
        return new Rect(rail.x() + 5, rail.y() + 115, rail.width() - 10, Math.max(1, rail.height() - 120));
    }

    public static Rect bottomDock(int screenWidth, int screenHeight, boolean expanded) {
        boolean reserveCorners = screenWidth >= 700;
        int leftReserved = reserveCorners ? CITIZEN_WIDTH + SCREEN_MARGIN + 12 : SCREEN_MARGIN;
        int rightReserved = reserveCorners ? RIGHT_RAIL_WIDTH + SCREEN_MARGIN + 12 : SCREEN_MARGIN;
        int available = Math.max(1, screenWidth - leftReserved - rightReserved);
        int desired = Math.min(BOTTOM_DOCK_MAX_WIDTH, available);
        int width = available >= BOTTOM_DOCK_MIN_WIDTH
                ? Math.max(BOTTOM_DOCK_MIN_WIDTH, desired)
                : Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int height = expanded ? BOTTOM_DOCK_EXPANDED_HEIGHT : BOTTOM_DOCK_COLLAPSED_HEIGHT;
        int x = reserveCorners && available >= BOTTOM_DOCK_MIN_WIDTH
                ? leftReserved + Math.max(0, (available - width) / 2)
                : Math.max(SCREEN_MARGIN, (screenWidth - width) / 2);
        int y = Math.max(TOP_HEIGHT + 14, screenHeight - height - SCREEN_MARGIN);
        return new Rect(x, y, width, height);
    }

    public static Rect bottomToggle(int screenWidth, int screenHeight, boolean expanded) {
        Rect dock = bottomDock(screenWidth, screenHeight, expanded);
        int x = dock.x() + (dock.width() - BOTTOM_TOGGLE_WIDTH) / 2;
        int y = Math.max(0, dock.y() - BOTTOM_TOGGLE_HEIGHT + 2);
        return new Rect(x, y, BOTTOM_TOGGLE_WIDTH, BOTTOM_TOGGLE_HEIGHT);
    }

    public static Rect bottomTab(int screenWidth, int screenHeight, int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Bottom tab index must be inside the toolbar");
        }
        Rect dock = bottomDock(screenWidth, screenHeight, true);
        int gap = 3;
        int available = dock.width() - 10 - gap * (count - 1);
        int width = Math.max(1, available / count);
        int used = width * count + gap * (count - 1);
        int start = dock.x() + (dock.width() - used) / 2;
        return new Rect(start + index * (width + gap), dock.y() + 5, width, BOTTOM_TAB_HEIGHT);
    }

    /** Context row used by the selected command tab. Currently Orders owns these six slots. */
    public static Rect bottomTool(int screenWidth, int screenHeight, int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Tool index must be inside the command row");
        }
        Rect dock = bottomDock(screenWidth, screenHeight, true);
        Rect hotbar = hotbarArea(screenWidth, screenHeight, true);
        int gap = 3;
        int y = dock.y() + BOTTOM_TAB_HEIGHT + 10;
        int height = Math.max(1, hotbar.y() - y - 4);
        int available = dock.width() - 10 - gap * (count - 1);
        int width = Math.max(1, available / count);
        int used = width * count + gap * (count - 1);
        int start = dock.x() + (dock.width() - used) / 2;
        return new Rect(start + index * (width + gap), y, width, height);
    }

    public static Rect hotbarArea(int screenWidth, int screenHeight, boolean expanded) {
        Rect dock = bottomDock(screenWidth, screenHeight, expanded);
        return new Rect(dock.x() + 5, dock.y() + dock.height() - HOTBAR_HEIGHT - 3,
                Math.max(1, dock.width() - 10), HOTBAR_HEIGHT);
    }

    public static Rect hotbarSlot(int screenWidth, int screenHeight, boolean expanded, int index) {
        if (index < 0 || index >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Hotbar index must be between 0 and 8");
        }
        Rect area = hotbarArea(screenWidth, screenHeight, expanded);
        int gap = 2;
        int maxSlot = 34;
        int slot = Math.max(1, Math.min(maxSlot, (area.width() - gap * (HOTBAR_SLOTS - 1)) / HOTBAR_SLOTS));
        int used = slot * HOTBAR_SLOTS + gap * (HOTBAR_SLOTS - 1);
        int start = area.x() + Math.max(0, (area.width() - used) / 2);
        return new Rect(start + index * (slot + gap), area.y(), slot, area.height());
    }

    public static Rect alerts(int screenWidth, int lineCount) {
        int count = Math.max(1, Math.min(3, lineCount));
        int width = Math.min(ALERT_WIDTH, Math.max(1, screenWidth - SCREEN_MARGIN * 2));
        int height = 6 + count * 22;
        return new Rect(Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN),
                TOP_HEIGHT + SCREEN_MARGIN + 8, width, height);
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }
}
