package dev.stonebanner.control;

/** Counts distinct physical presses, not keyboard autorepeat. */
public final class DoublePressGesture {
    private long previousPress = -1;
    private boolean held;

    public boolean press(long nowMillis, int intervalMillis) {
        if (held) return false;
        held = true;
        boolean doublePress = previousPress >= 0 && nowMillis >= previousPress
                && nowMillis - previousPress <= intervalMillis;
        previousPress = doublePress ? -1 : nowMillis;
        return doublePress;
    }

    public void release() { held = false; }
    public void reset() { held = false; previousPress = -1; }
}
