package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoublePressGestureTest {
    @Test void onlyDistinctClosePressesCount() {
        var g=new DoublePressGesture();
        assertFalse(g.press(100,350));
        assertFalse(g.press(200,350)); // autorepeat still held
        g.release();
        assertTrue(g.press(300,350));
        g.release();
        assertFalse(g.press(330,350)); // third press begins new pair
    }
    @Test void timeoutClockResetAndGuiReset() {
        var g=new DoublePressGesture();
        assertFalse(g.press(100,250));g.release();
        assertFalse(g.press(400,250));g.release();
        g.reset();
        assertFalse(g.press(410,250));g.release();
        assertFalse(g.press(5,250));
    }
}
