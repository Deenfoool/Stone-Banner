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
    void collapsedDockKeepsTheRealHotbarVisible() {
        int width = 1280;
        int height = 720;
        StoneBannerHudLayout.Rect expanded = StoneBannerHudLayout.bottomDock(width, height, true);
        StoneBannerHudLayout.Rect collapsed = StoneBannerHudLayout.bottomDock(width, height, false);
        StoneBannerHudLayout.Rect hotbar = StoneBannerHudLayout.hotbarArea(width, height, false);

        assertTrue(collapsed.height() < expanded.height());
        assertTrue(collapsed.contains(hotbar.x(), hotbar.y()));
        assertTrue(collapsed.contains(hotbar.x() + hotbar.width() - 1, hotbar.y() + hotbar.height() - 1));
    }

    @Test
    void nineHotbarSlotsStayInsideBottomRowWithoutOverlap() {
        int width = 870;
        int height = 519;
        StoneBannerHudLayout.Rect area = StoneBannerHudLayout.hotbarArea(width, height, true);
        StoneBannerHudLayout.Rect previous = null;
        for (int index = 0; index < StoneBannerHudLayout.HOTBAR_SLOTS; index++) {
            StoneBannerHudLayout.Rect slot = StoneBannerHudLayout.hotbarSlot(width, height, true, index);
            assertTrue(area.contains(slot.x(), slot.y()));
            assertTrue(area.contains(slot.x() + slot.width() - 1, slot.y() + slot.height() - 1));
            if (previous != null) {
                assertFalse(previous.overlaps(slot));
            }
            previous = slot;
        }
    }

    @Test
    void ordersRowLivesBetweenTabsAndHotbar() {
        int width = 870;
        int height = 519;
        StoneBannerHudLayout.Rect firstTab = StoneBannerHudLayout.bottomTab(width, height, 0, 5);
        StoneBannerHudLayout.Rect firstTool = StoneBannerHudLayout.bottomTool(width, height, 0, 6);
        StoneBannerHudLayout.Rect hotbar = StoneBannerHudLayout.hotbarArea(width, height, true);

        assertTrue(firstTool.y() > firstTab.y() + firstTab.height());
        assertTrue(firstTool.y() + firstTool.height() < hotbar.y());
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
