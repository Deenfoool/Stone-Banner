package dev.stonebanner.control;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScreenInputRulesTest {
    @Test void centerDoesNotScroll(){assertArrayEquals(new double[]{0,0},ScreenInputRules.edgePan(100,100,200,200,10));}
    @Test void edgesScrollInExpectedDirections(){
        assertArrayEquals(new double[]{1,0},ScreenInputRules.edgePan(0,100,200,200,10));
        assertArrayEquals(new double[]{-1,0},ScreenInputRules.edgePan(200,100,200,200,10));
        assertArrayEquals(new double[]{0,1},ScreenInputRules.edgePan(100,0,200,200,10));
        assertArrayEquals(new double[]{0,-1},ScreenInputRules.edgePan(100,200,200,200,10));
    }
    @Test void allCornersHaveSameSpeed(){for(int x:new int[]{0,200})for(int y:new int[]{0,200}){var v=ScreenInputRules.edgePan(x,y,200,200,10);assertEquals(1,Math.hypot(v[0],v[1]),1e-9);}}
    @Test void outsideWindowDoesNotScroll(){assertArrayEquals(new double[]{0,0},ScreenInputRules.edgePan(-1,0,200,200,10));}
    @Test void rectangleWorksForEveryDragDirection(){for(int x1:new int[]{10,90})for(int y1:new int[]{20,80}){assertTrue(ScreenInputRules.inRectangle(50,50,x1,y1,100-x1,100-y1));assertFalse(ScreenInputRules.inRectangle(100,100,x1,y1,100-x1,100-y1));}}
}
