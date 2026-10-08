package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolBackGestureTest {
    @Test void singlePressRewindsAndDoublePressCancelsEntireSelection() {
        var gesture = new ToolBackGesture();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1000, 300, true, true));
        gesture.release();
        assertEquals(ToolBackGesture.Result.CLEAR, gesture.press(1180, 300, true, true));
        gesture.release();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1500, 300, true, true));
    }

    @Test void noStepPressExitsToolAndSecondClickCannotIssueWorldOrder() {
        var gesture = new ToolBackGesture();
        assertEquals(ToolBackGesture.Result.EXIT, gesture.press(1000, 300, true, false));
        gesture.release();
        assertEquals(ToolBackGesture.Result.CONSUME, gesture.press(1150, 300, false, false));
        gesture.release();
        assertEquals(ToolBackGesture.Result.NONE, gesture.press(1200, 300, false, false));
    }

    @Test void physicalReleaseIsNecessaryAndTooLateClicksStaySingle() {
        var gesture = new ToolBackGesture();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1000, 300, true, true));
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1050, 300, true, true));
        gesture.release();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1400, 300, true, true));
        gesture.reset();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1450, 300, true, true));
    }

    @Test void resetOnToolSwitchRejectsOldDoubleClick() {
        var gesture = new ToolBackGesture();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1000, 300, true, true));
        gesture.release();
        gesture.reset();
        assertEquals(ToolBackGesture.Result.REWIND, gesture.press(1090, 300, true, true));
    }
}
