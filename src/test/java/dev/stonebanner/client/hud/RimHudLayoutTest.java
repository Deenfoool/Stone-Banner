package dev.stonebanner.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RimHudLayoutTest {
    @Test
    void citizenInspectorGrowsUpwardAboveBottomToolbar() {
        RimHudLayout.Rect bar = RimHudLayout.bottomBar(870, 519);
        RimHudLayout.Rect header = RimHudLayout.citizenHeader(519);
        RimHudLayout.Rect panel = RimHudLayout.citizenPanel(519);

        assertEquals(487, bar.y());
        assertTrue(header.y() + header.height() < bar.y());
        assertEquals(header.y(), panel.y() + panel.height());
    }

    @Test
    void bottomTabsRemainCenteredAndDoNotOverlap() {
        RimHudLayout.Rect first = RimHudLayout.bottomButton(870, 519, 0, 4);
        RimHudLayout.Rect last = RimHudLayout.bottomButton(870, 519, 3, 4);

        assertTrue(first.x() >= RimHudLayout.SCREEN_MARGIN);
        assertTrue(last.x() + last.width() <= 870 - RimHudLayout.SCREEN_MARGIN);
        assertTrue(first.x() + first.width() < last.x());
        assertEquals(first.y(), last.y());
    }

    @Test
    void timeControlsStayInsideTopRightPanel() {
        RimHudLayout.Rect panel = RimHudLayout.timePanel(870);
        for (int index = 0; index < 4; index++) {
            RimHudLayout.Rect button = RimHudLayout.timeButton(870, index);
            assertTrue(panel.contains(button.x(), button.y()));
            assertTrue(panel.contains(button.x() + button.width() - 1, button.y() + button.height() - 1));
        }
    }

    @Test
    void hitTestingUsesHalfOpenBounds() {
        RimHudLayout.Rect rect = new RimHudLayout.Rect(8, 20, 100, 30);

        assertTrue(rect.contains(8, 20));
        assertTrue(rect.contains(107.9, 49.9));
        assertFalse(rect.contains(108, 30));
        assertFalse(rect.contains(20, 50));
    }
}
