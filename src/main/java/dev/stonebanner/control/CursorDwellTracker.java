package dev.stonebanner.control;

/** A cursor hint appears only after a stable target and a short period of stillness. */
public final class CursorDwellTracker {
    private String target;
    private double x, y;
    private long sinceMillis;

    public void observe(String targetKey, double px, double py, long nowMillis, double tolerancePx) {
        if (targetKey == null || targetKey.isEmpty()) { reset(); return; }
        double deltaX = px - x, deltaY = py - y;
        if (!targetKey.equals(target) || nowMillis < sinceMillis
                || deltaX * deltaX + deltaY * deltaY > tolerancePx * tolerancePx) {
            target = targetKey;
            sinceMillis = nowMillis;
        }
        x = px;
        y = py;
    }

    public boolean ready(long nowMillis, int delayMillis) {
        return target != null && nowMillis >= sinceMillis && nowMillis - sinceMillis >= delayMillis;
    }

    public void reset() { target = null; sinceMillis = 0; x = 0; y = 0; }
}
