package dev.stonebanner.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMapRefreshStateTest {
    @Test
    void firstFrameAlwaysSamplesIncludingSpawnAtOrigin() {
        var state = new MiniMapRefreshState();
        var world = new Object();
        for (int x : new int[]{0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertTrue(state.shouldRefresh(world, x, 64, 0, 0));
        }
    }

    @Test
    void stationaryMapRefreshesOnTickBudgetNotEveryFrame() {
        var state = new MiniMapRefreshState();
        var world = new Object();
        state.refreshed(world, 0, 64, 0, 100);
        assertFalse(state.shouldRefresh(world, 0, 64, 0, 100));
        assertFalse(state.shouldRefresh(world, 0, 64, 0, 119));
        assertTrue(state.shouldRefresh(world, 0, 64, 0, 120));
    }

    @Test
    void movementIncludingHeightInvalidatesColorsWithoutWaiting() {
        var state = new MiniMapRefreshState();
        var world = new Object();
        state.refreshed(world, 0, 64, 0, 100);
        assertFalse(state.shouldRefresh(world, 3, 67, -3, 101));
        assertTrue(state.shouldRefresh(world, 4, 64, 0, 101));
        assertTrue(state.shouldRefresh(world, 0, 64, -4, 101));
        assertTrue(state.shouldRefresh(world, 0, 68, 0, 101));
        state.refreshed(world, Integer.MIN_VALUE, 64, 0, 100);
        assertTrue(state.shouldRefresh(world, Integer.MAX_VALUE, 64, 0, 101));
    }

    @Test
    void worldChangeAndClockRollbackRefreshEvenAtSameCoordinates() {
        var state = new MiniMapRefreshState();
        var world = new Object();
        state.refreshed(world, 0, 64, 0, 100);
        assertTrue(state.shouldRefresh(new Object(), 0, 64, 0, 100));
        assertTrue(state.shouldRefresh(world, 0, 64, 0, 0));
    }
}
