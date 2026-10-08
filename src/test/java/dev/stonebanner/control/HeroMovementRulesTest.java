package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroMovementRulesTest {
    @Test void doubleClickRequiresTimeAndNearbyCursor() {
        var clicks=new GroundClickTracker();
        assertFalse(clicks.click(100,20,30,300));
        assertTrue(clicks.click(250,24,32,300));
        assertFalse(clicks.click(260,24,32,300)); // third press is a new pair, not another double
        assertFalse(clicks.click(800,24,32,300));
        assertFalse(clicks.click(850,40,32,300));
    }
    @Test void uiResetAndClockChangeNeverCreateDoubleClicks() {
        var clicks=new GroundClickTracker();clicks.click(100,20,30,300);clicks.reset();
        assertFalse(clicks.click(200,20,30,300));
        assertFalse(clicks.click(10,20,30,300));
    }
    @Test void retriesBackOffAndExhaustWithoutResettingOnProgress() {
        var budget=new RouteRetryBudget();
        assertEquals(RouteRetryBudget.Decision.RETRY,budget.request(0));
        assertEquals(RouteRetryBudget.Decision.WAIT,budget.request(19));
        assertEquals(RouteRetryBudget.Decision.RETRY,budget.request(20));
        assertEquals(RouteRetryBudget.Decision.WAIT,budget.request(59));
        assertEquals(RouteRetryBudget.Decision.RETRY,budget.request(60));
        assertEquals(RouteRetryBudget.Decision.WAIT,budget.request(139));
        assertEquals(RouteRetryBudget.Decision.EXHAUSTED,budget.request(140));
        budget.reset();assertEquals(RouteRetryBudget.Decision.RETRY,budget.request(141));
    }
    @Test void responseRampsAndStopsWithoutNegativeSpeed() {
        var response=new MovementResponse();
        assertEquals(.25f,response.update(1),.0001);
        assertEquals(.5f,response.update(1),.0001);
        assertEquals(.75f,response.update(1),.0001);
        assertEquals(1,response.update(1),.0001);
        float previous=1;
        for(int i=0;i<5;i++){float value=response.update(0);assertTrue(value>=0 && value<=previous);previous=value;}
        assertEquals(0,previous,.0001);
        response.reset();assertEquals(.15f,response.update(.15f),.0001);
    }
    @Test void arrivalBrakingIsMonotonicAndKeepsAReachableFinalApproach() {
        assertEquals(1,MovementResponse.arrivalScale(4),.0001);
        assertTrue(MovementResponse.arrivalScale(.9)<MovementResponse.arrivalScale(1.5));
        assertTrue(MovementResponse.arrivalScale(.26)>0);
        assertEquals(.15f,MovementResponse.arrivalScale(0),.0001);
    }
}
