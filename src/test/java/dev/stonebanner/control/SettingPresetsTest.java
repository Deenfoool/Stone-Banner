package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingPresetsTest {
    @Test void intPresetsAdvanceAndWrap() {
        int[] v = {180, 230, 300, 400};
        assertEquals(1, SettingPresets.nextIndex(v, 180));
        assertEquals(2, SettingPresets.nextIndex(v, 250));
        assertEquals(0, SettingPresets.nextIndex(v, 400));
    }
    @Test void legacyCustomValuesAdvanceWithoutReset() {
        double[] v = {0.25, 0.50, 1.0, 2.0};
        assertEquals(2, SettingPresets.nextIndex(v, 0.76));
        assertEquals(0, SettingPresets.nextIndex(v, 2.1));
        assertEquals(1, SettingPresets.nextIndex(v, 0.25));
    }
    @Test void emptyPresetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SettingPresets.nextIndex(new int[0], 123));
        assertThrows(IllegalArgumentException.class, () -> SettingPresets.nextIndex(new double[0], 1.0));
    }
}
