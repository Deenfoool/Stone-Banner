package dev.stonebanner.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoneBannerHudLayoutTest {
    @Test
    void miniMapRemainsVisibleAndSeparateFromControlsOnSmallViewports() {
        for (int[] resolution : new int[][]{{320, 240}, {427, 240}, {427, 280}, {640, 300}, {640, 360}, {870, 519}}) {
            int w = resolution[0], h = resolution[1];
            var rail = StoneBannerHudLayout.rightRail(w, h);
            var map = StoneBannerHudLayout.miniMap(w, h);
            assertTrue(map.width() >= 23 && map.height() >= 23);
            assertTrue(rail.contains(map.x(), map.y()));
            assertTrue(rail.contains(map.x() + map.width() - 1, map.y() + map.height() - 1));
            for (int i = 0; i < 4; i++) assertFalse(map.overlaps(StoneBannerHudLayout.timeButton(w, h, i)));
            for (int i = 0; i < 3; i++) assertFalse(map.overlaps(StoneBannerHudLayout.layerButton(w, h, i)));
        }
    }

    @Test
    void compactPanelsUseLessSpaceWithoutShrinkingItemIcons() {
        assertTrue(StoneBannerHudLayout.TOP_HEIGHT <= 34);
        assertTrue(StoneBannerHudLayout.BOTTOM_DOCK_EXPANDED_HEIGHT <= 106);
        assertTrue(StoneBannerHudLayout.CITIZEN_EXPANDED_HEIGHT <= 186);
        assertTrue(StoneBannerHudLayout.RIGHT_RAIL_HEIGHT <= 156);
        assertTrue(StoneBannerHudLayout.CITIZEN_TAB_HEIGHT >= 20);
        assertTrue(StoneBannerHudLayout.HOTBAR_HEIGHT >= 24);
    }

    @Test
    void narrowViewportsStackCornersAboveDockWithoutOverlap() {
        for (int[] resolution : new int[][]{{320, 240}, {427, 240}, {640, 360}, {690, 400}, {700, 400}}) {
            int w = resolution[0], h = resolution[1];
            var dock = StoneBannerHudLayout.bottomDock(w, h, true);
            var toggle = StoneBannerHudLayout.bottomToggle(w, h, true);
            var rail = StoneBannerHudLayout.rightRail(w, h);
            boolean expanded = StoneBannerHudLayout.canExpandCitizen(w, h);
            var citizen = StoneBannerHudLayout.citizenCard(w, h, expanded);
            var group = StoneBannerHudLayout.groupCard(w, h);
            assertFalse(citizen.overlaps(dock));
            assertFalse(group.overlaps(dock));
            assertFalse(rail.overlaps(dock));
            assertFalse(citizen.overlaps(rail));
            assertFalse(group.overlaps(rail));
            assertFalse(citizen.overlaps(toggle));
            assertFalse(rail.overlaps(toggle));
            for (var rect : new StoneBannerHudLayout.Rect[]{dock, rail, citizen, group, toggle}) {
                assertTrue(rect.x() >= 0 && rect.y() >= 0);
                assertTrue(rect.x() + rect.width() <= w);
                assertTrue(rect.y() + rect.height() <= h);
            }
            for (int i = 0; i < 4; i++) {
                var button = StoneBannerHudLayout.timeButton(w, h, i);
                assertTrue(rail.contains(button.x(), button.y()));
                assertTrue(rail.contains(button.x() + button.width() - 1, button.y() + button.height() - 1));
            }
        }
    }

    @Test
    void compactDockKeepsAllRowsAndSlotsSeparatedOnSmallScreens() {
        for (int w : new int[]{320, 427, 640, 690, 870, 1280}) {
            var dock = StoneBannerHudLayout.bottomDock(w, 400, true);
            var hotbar = StoneBannerHudLayout.hotbarArea(w, 400, true);
            for (int i = 0; i < 5; i++) {
                var tab = StoneBannerHudLayout.bottomTab(w, 400, i, 5);
                var tool = StoneBannerHudLayout.bottomTool(w, 400, i, 5);
                assertFalse(tab.overlaps(tool));
                assertFalse(tool.overlaps(hotbar));
                assertTrue(dock.contains(tool.x() + tool.width() - 1, tool.y() + tool.height() - 1));
            }
            for (int i = 0; i < 9; i++) {
                var slot = StoneBannerHudLayout.hotbarSlot(w, 400, true, i);
                assertTrue(hotbar.contains(slot.x(), slot.y()));
                assertTrue(hotbar.contains(slot.x() + slot.width() - 1, slot.y() + slot.height() - 1));
            }
        }
    }

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
    void groupOverviewDoesNotCoverDockOrTimeRail() {
        for (int[] resolution : new int[][]{{870, 519}, {1280, 720}}) {
            int w = resolution[0], h = resolution[1];
            var card = StoneBannerHudLayout.groupCard(w, h);
            assertFalse(card.overlaps(StoneBannerHudLayout.bottomDock(w, h, true)));
            assertFalse(card.overlaps(StoneBannerHudLayout.rightRail(w, h)));
        }
    }

    @Test
    void threeHierarchyCategoriesFitInsideOrdersRow() {
        var area = StoneBannerHudLayout.bottomDock(870, 519, true);
        for (int i = 0; i < 3; i++) {
            var slot = StoneBannerHudLayout.bottomTool(870, 519, i, 3);
            assertTrue(area.contains(slot.x(), slot.y()));
            assertTrue(area.contains(slot.x() + slot.width() - 1, slot.y() + slot.height() - 1));
        }
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
    void mapLayersFitBetweenTimeControlsAndClock() {
        var clock = StoneBannerHudLayout.clockPanel(870, 519);
        var rail = StoneBannerHudLayout.rightRail(870, 519);
        for (int i = 0; i < 3; i++) {
            var button = StoneBannerHudLayout.layerButton(870, 519, i);
            assertTrue(rail.contains(button.x(), button.y()));
            assertTrue(rail.contains(button.x()+button.width()-1, button.y()+button.height()-1));
            assertFalse(button.overlaps(clock));
            for (int j = 0; j < 4; j++) assertFalse(button.overlaps(StoneBannerHudLayout.timeButton(870, 519, j)));
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
