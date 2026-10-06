package dev.stonebanner.designation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationAccessStatusTest {
    @Test
    void stableCodesRoundTrip() {
        for (ExcavationAccessStatus status : ExcavationAccessStatus.values()) {
            assertEquals(status, ExcavationAccessStatus.fromCode(status.code()));
        }
    }

    @Test
    void unknownCodeFailsClosedAsNoPath() {
        assertEquals(ExcavationAccessStatus.NO_PATH, ExcavationAccessStatus.fromCode(999));
    }
}
