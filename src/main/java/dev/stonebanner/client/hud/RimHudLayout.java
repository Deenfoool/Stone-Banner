package dev.stonebanner.client.hud;

/** Pure layout calculations for the RimWorld-inspired tactical HUD. */
public final class RimHudLayout {
    public static final int SCREEN_MARGIN = 8;
    public static final int BOTTOM_BAR_HEIGHT = 32;
    public static final int BOTTOM_BUTTON_HEIGHT = 26;
    public static final int BOTTOM_BUTTON_GAP = 3;
    public static final int CITIZEN_WIDTH = 220;
    public static final int CITIZEN_HEADER_HEIGHT = 30;
    public static final int CITIZEN_PANEL_HEIGHT = 132;
    public static final int CONTEXT_HEIGHT = 30;
    public static final int TIME_PANEL_WIDTH = 170;
    public static final int TIME_PANEL_HEIGHT = 30;
    public static final int TIME_BUTTON_GAP = 3;

    private RimHudLayout() {
    }

    public static Rect bottomBar(int screenWidth, int screenHeight) {
        return new Rect(0, Math.max(0, screenHeight - BOTTOM_BAR_HEIGHT), Math.max(0, screenWidth), BOTTOM_BAR_HEIGHT);
    }

    public static Rect citizenHeader(int screenHeight) {
        int y = Math.max(42, screenHeight - BOTTOM_BAR_HEIGHT - CITIZEN_HEADER_HEIGHT - 4);
        return new Rect(SCREEN_MARGIN, y, CITIZEN_WIDTH, CITIZEN_HEADER_HEIGHT);
    }

    public static Rect citizenPanel(int screenHeight) {
        Rect header = citizenHeader(screenHeight);
        int available = Math.max(0, header.y() - 44);
        int height = Math.min(CITIZEN_PANEL_HEIGHT, available);
        return new Rect(header.x(), header.y() - height, header.width(), height);
    }

    public static Rect bottomButton(int screenWidth, int screenHeight, int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Button index must be inside the toolbar");
        }
        int maxWidth = Math.max(0, screenWidth - SCREEN_MARGIN * 2);
        int preferredWidth = count <= 4 ? 132 : 102;
        int totalPreferred = preferredWidth * count + BOTTOM_BUTTON_GAP * (count - 1);
        int buttonWidth = totalPreferred <= maxWidth
                ? preferredWidth
                : Math.max(48, (maxWidth - BOTTOM_BUTTON_GAP * (count - 1)) / count);
        int totalWidth = buttonWidth * count + BOTTOM_BUTTON_GAP * (count - 1);
        int startX = Math.max(SCREEN_MARGIN, (screenWidth - totalWidth) / 2);
        int y = Math.max(0, screenHeight - BOTTOM_BAR_HEIGHT + 3);
        return new Rect(startX + index * (buttonWidth + BOTTOM_BUTTON_GAP), y, buttonWidth, BOTTOM_BUTTON_HEIGHT);
    }

    public static int contextY(int screenHeight) {
        return Math.max(42, screenHeight - BOTTOM_BAR_HEIGHT - CONTEXT_HEIGHT - 7);
    }

    public static Rect timePanel(int screenWidth) {
        return new Rect(Math.max(SCREEN_MARGIN, screenWidth - TIME_PANEL_WIDTH - SCREEN_MARGIN),
                SCREEN_MARGIN, TIME_PANEL_WIDTH, TIME_PANEL_HEIGHT);
    }

    public static Rect timeButton(int screenWidth, int index) {
        int[] widths = {55, 48, 27, 27};
        if (index < 0 || index >= widths.length) {
            throw new IllegalArgumentException("Time button index must be between 0 and 3");
        }
        Rect panel = timePanel(screenWidth);
        int x = panel.x() + 4;
        for (int i = 0; i < index; i++) x += widths[i] + TIME_BUTTON_GAP;
        return new Rect(x, panel.y() + 4, widths[index], panel.height() - 8);
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
