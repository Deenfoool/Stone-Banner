package dev.stonebanner.control;

/**
 * Designation-specific right-click gesture. Consumes both clicks of an exit double-tap,
 * so a second RMB never escapes into movement/combat after the tool closes.
 */
public final class ToolBackGesture {
    public enum Result { NONE, REWIND, CLEAR, EXIT, CONSUME }
    private final DoublePressGesture doublePress = new DoublePressGesture();
    private long lastExit = -1;
    private boolean held;

    public Result press(long now, int interval, boolean toolActive, boolean hasProgress) {
        // Key autorepeat and duplicate mouse dispatches are not distinct physical clicks.
        if (held) return Result.CONSUME;
        held = true;
        if (!toolActive) {
            if (lastExit >= 0 && now >= lastExit && now - lastExit <= interval) {
                lastExit = -1;
                doublePress.reset();
                return Result.CONSUME;
            }
            lastExit = -1;
            doublePress.reset();
            return Result.NONE;
        }
        lastExit = -1;
        if (doublePress.press(now, interval)) return Result.CLEAR;
        if (hasProgress) return Result.REWIND;
        lastExit = now;
        return Result.EXIT;
    }

    public void release() { held = false; doublePress.release(); }
    public void reset() { held = false; doublePress.reset(); lastExit = -1; }
}
