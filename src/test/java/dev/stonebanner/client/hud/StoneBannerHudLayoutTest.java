package dev.stonebanner.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoneBannerHudLayoutTest {
    @Test
    void mainHudZonesProtectTheCenterAtCommonGuiScale() {
        int width = 870;
        int height = 519;

        StoneBannerHudLayout.Rect citizen = StoneBannerHudLayout.citizenCard(width, height, true);
        StoneBannerHudLayout.Rect dock = StoneBannerHudLayout.bottomDock(width, height, true);
        StoneBannerHudLayout.Rect rail = StoneBannerHudLayout.rightRail(width, height);

        assertFalse(citizen.overlaps(dock));
        assertFalse(dock.overlaps(rail));
        assertFalse(citizen.overlaps(rail));
    }

    @Test
    void collapsedDockLeavesOnlyThinHandleAtBottom() {
        StoneBannerHudLayout.Rect expanded = StoneBannerHudLayout.bottomDock(1280, 720, true);
        StoneBannerHudLayout.Rect collapsed = StoneBannerHudLayout.bottomDock(1280, 720, false);

        assertTrue(collapsed.height() < expanded.height());
        assertTrue(collapsed.height() <= StoneBannerHudLayout.BOTTOM_DOCK_COLLAPSED_HEIGHT);
    }

    @Test
    void speedButtonsRemainInsideRightRail() {
        StoneBannerHudLayout.Rect rail = StoneBannerHudLayout.rightRail(870, 519);
        for (int index = 0; index < 4; index++) {
            StoneBannerHudLayout.Rect button = StoneBannerHudLayout.timeButton(870, 519, index);
            assertTrue(rail.contains(button.x(), button.y()));
            assertTrue(rail.contains(button.x() + button.width() - 1, button.y() + button.height() - 1));
        }
    }

    @Test
    void citizenTabsStayInsideExpandedCard() {
        StoneBannerHudLayout.Rect card = StoneBannerHudLayout.citizenCard(870, 519, true);
        for (int index = 0; index < 5; index++) {
            StoneBannerHudLayout.Rect tab = StoneBannerHudLayout.citizenTab(870, 519, true, index, 5);
            assertTrue(card.contains(tab.x(), tab.y()));
            assertTrue(card.contains(tab.x() + tab.width() - 1, tab.y() + tab.height() - 1));
        }
    }
}
