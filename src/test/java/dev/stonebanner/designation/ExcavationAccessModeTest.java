package dev.stonebanner.designation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationAccessModeTest {
    @Test
    void cyclesThroughAllStrategies() {
        assertEquals(ExcavationAccessMode.RAMP, ExcavationAccessMode.AUTO.next());
        assertEquals(ExcavationAccessMode.LADDERS, ExcavationAccessMode.RAMP.next());
        assertEquals(ExcavationAccessMode.AUTO, ExcavationAccessMode.LADDERS.next());
    }

    @Test
    void parsingIsStableAndUnknownValuesFallBackToAuto() {
        assertEquals(ExcavationAccessMode.LADDERS, ExcavationAccessMode.fromSerializedName("LADDERS"));
        assertEquals(ExcavationAccessMode.AUTO, ExcavationAccessMode.fromSerializedName("unknown"));
        assertEquals(ExcavationAccessMode.AUTO, ExcavationAccessMode.byId(999));
    }
}
