package dev.stonebanner.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlModeTest {
    @Test
    void cyclesThroughAllModes() {
        assertEquals(ControlMode.HYBRID, ControlMode.ACTION.next());
        assertEquals(ControlMode.ACTION, ControlMode.TACTICAL.next());
        assertEquals(ControlMode.ACTION, ControlMode.HYBRID.next());
    }
}
