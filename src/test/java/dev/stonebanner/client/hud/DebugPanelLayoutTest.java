package dev.stonebanner.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugPanelLayoutTest {
    @Test void largeScreenKeepsAllRowsBelowTopBar() {
        var layout = DebugPanelLayout.fit(960, 540, 8).orElseThrow();
        assertEquals(320, layout.width());
        assertEquals(8, layout.rows());
        assertEquals(56, layout.y());
        assertEquals(952, layout.x() + layout.width());
    }

    @Test void narrowScreenKeepsMarginsAndLeavesBottomDockClear() {
        var layout = DebugPanelLayout.fit(200, 210, 8).orElseThrow();
        assertEquals(8, layout.x());
        assertEquals(184, layout.width());
        assertEquals(4, layout.rows());
        assertTrue(layout.y() + layout.height() <= 110);
    }

    @Test void tinyScreenDoesNotProduceUnreadablePanel() {
        assertTrue(DebugPanelLayout.fit(100, 540, 8).isEmpty());
        assertTrue(DebugPanelLayout.fit(960, 180, 8).isEmpty());
    }

    @Test void emptySnapshotDoesNotProducePanel() {
        assertTrue(DebugPanelLayout.fit(960, 540, 0).isEmpty());
    }
}
