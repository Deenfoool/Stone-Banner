package dev.stonebanner.client.hud;

/** Cache lifetime independent of rendering, with no sentinel arithmetic on the first frame. */
final class MiniMapRefreshState {
    private Object world;
    private long lastRefresh;
    private int x;
    private int y;
    private int z;

    boolean shouldRefresh(Object currentWorld, int currentX, int currentY, int currentZ, long ticks) {
        if (world == null || world != currentWorld || ticks < lastRefresh) return true;
        return Math.abs((long) currentX - x) >= 4 || Math.abs((long) currentZ - z) >= 4
                || Math.abs((long) currentY - y) >= 4 || ticks - lastRefresh >= 20;
    }

    void refreshed(Object currentWorld, int currentX, int currentY, int currentZ, long ticks) {
        world = currentWorld;
        x = currentX;
        y = currentY;
        z = currentZ;
        lastRefresh = ticks;
    }
}
