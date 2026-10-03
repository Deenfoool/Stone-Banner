package dev.stonebanner.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanNpcAttributesTest {
    @Test
    void normalMovementSpeedUsesPlayerLikeWalkingPace() {
        assertEquals(0.34D, HumanNpcEntity.BASE_MOVEMENT_SPEED, 1.0E-9D);
    }
}
