package dev.stonebanner.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenBrainStateTest {
    @Test
    void resolvesKnownSerializedState() {
        assertEquals(CitizenBrainState.FOLLOW, CitizenBrainState.fromSerializedName("follow"));
    }

    @Test
    void fallsBackToIdleForUnknownSerializedState() {
        assertEquals(CitizenBrainState.IDLE, CitizenBrainState.fromSerializedName("unknown"));
    }

    @Test
    void fallsBackToIdleForInvalidOrdinal() {
        assertEquals(CitizenBrainState.IDLE, CitizenBrainState.byId(99));
    }
}
