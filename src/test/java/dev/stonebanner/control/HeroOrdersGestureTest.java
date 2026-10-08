package dev.stonebanner.control;

import org.junit.jupiter.api.Test;

import static dev.stonebanner.control.HeroOrdersGesture.Change.*;
import static org.junit.jupiter.api.Assertions.*;

class HeroOrdersGestureTest {
    @Test void tapFromHeroLeavesOrdersEnabled() {
        var g = new HeroOrdersGesture();
        assertEquals(ENTER_ORDERS,g.press(100,false));
        assertEquals(NONE,g.release(150,220,true));
        assertFalse(g.pressed());
        assertEquals(RETURN_HERO,g.press(200,true));
        assertEquals(NONE,g.release(250,220,false));
    }
    @Test void holdFromHeroReturnsToHeroOnRelease() {
        var g = new HeroOrdersGesture();
        assertEquals(ENTER_ORDERS,g.press(100,false));
        assertEquals(NONE,g.press(150,true)); // repeat does not toggle or reset the clock
        assertEquals(RETURN_HERO,g.release(350,220,true));
        assertEquals(NONE,g.release(351,220,false));
    }
    @Test void disablingHoldTabRetainsOrdersOnRelease() {
        var g = new HeroOrdersGesture();
        assertEquals(ENTER_ORDERS, g.press(100, false));
        assertEquals(NONE, g.release(2500, Integer.MAX_VALUE, true));
        assertEquals(RETURN_HERO, g.press(2600, true));
    }

    @Test void holdStartedInOrdersOnlyLeavesOnce() {
        var g = new HeroOrdersGesture();
        assertEquals(RETURN_HERO,g.press(100,true));
        assertEquals(NONE,g.release(550,200,false));
    }
    @Test void interruptionCannotLeaveTemporaryOrdersOrReplayLater() {
        var g = new HeroOrdersGesture();
        assertEquals(ENTER_ORDERS,g.press(100,false));
        assertEquals(RETURN_HERO,g.interrupt(true));
        assertEquals(NONE,g.release(1000,220,false));
        assertEquals(NONE,g.interrupt(false));
    }
    @Test void interruptsRespectExternalModeChanges() {
        var g = new HeroOrdersGesture();
        g.press(100,false);
        assertEquals(NONE,g.interrupt(false));
        g.press(200,false);
        assertEquals(NONE,g.release(500,200,false));
    }
}
