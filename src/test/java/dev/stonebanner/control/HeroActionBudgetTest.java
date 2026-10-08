package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroActionBudgetTest {
    @Test void pursuitExpiresEvenWithAStationaryReachableTarget(){
        assertTrue(HeroActionBudget.permits(199,0,1));
        assertFalse(HeroActionBudget.permits(200,0,1));
        assertFalse(HeroActionBudget.permits(-1,0,1));
    }
    @Test void travelAndTargetDistanceBoundIndependentMovingTargets(){
        assertTrue(HeroActionBudget.permits(10,24*24,32*32));
        assertFalse(HeroActionBudget.permits(10,25*25,1));
        assertFalse(HeroActionBudget.permits(10,1,33*33));
        assertFalse(HeroActionBudget.permits(10,Double.NaN,1));
        assertFalse(HeroActionBudget.permits(10,0,Double.POSITIVE_INFINITY));
    }
    @Test void facingCrossesYawSeamWithoutSpinningAround(){
        assertEquals(181,HeroActionBudget.turn(179,-179,12));
        assertEquals(-181,HeroActionBudget.turn(-179,179,12));
        assertEquals(12,HeroActionBudget.turn(0,90,12));
        assertEquals(-12,HeroActionBudget.turn(0,-90,12));
    }
}
