package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CursorDwellTrackerTest {
    @Test void onlyStableTargetTriggersHint() {
        var t = new CursorDwellTracker();
        t.observe("block:1", 100, 100, 1000, 6);
        assertFalse(t.ready(1399, 400));
        t.observe("block:1", 103, 101, 1250, 6);
        assertTrue(t.ready(1400, 400));
        t.observe("block:2", 103, 101, 1450, 6);
        assertFalse(t.ready(1450, 400));
        assertTrue(t.ready(1850, 400));
    }

    @Test void movingOverToleranceOrUiHidesHint() {
        var t = new CursorDwellTracker();
        t.observe("entity:x", 10, 10, 1000, 5);
        t.observe("entity:x", 17, 10, 1390, 5);
        assertFalse(t.ready(1400, 400));
        t.observe(null, 17, 10, 1500, 5);
        assertFalse(t.ready(2500, 400));
    }

    @Test void slowContinuousDriftNeverTriggersStationaryHint() {
        var t = new CursorDwellTracker();
        for (int i = 0; i <= 12; i++)
            t.observe("block:1", i * 2.0, 20, 1000 + 50L * i, 6);
        assertFalse(t.ready(1610, 400));
    }

    @Test void durationCanBeConfiguredAndClockRollbackResets() {
        var t = new CursorDwellTracker();
        t.observe("b", 0, 0, 1000, 7);
        assertTrue(t.ready(1500, 200));
        t.observe("b", 0, 0, 100, 7);
        assertFalse(t.ready(150, 200));
    }
}
