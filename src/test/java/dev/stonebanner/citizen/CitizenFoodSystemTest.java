package dev.stonebanner.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenFoodSystemTest {
    @Test
    void nutritionMapsToStableHungerRelief() {
        assertEquals(0.0D, CitizenFoodSystem.reliefForNutrition(-2));
        assertEquals(0.0D, CitizenFoodSystem.reliefForNutrition(0));
        assertEquals(40.0D, CitizenFoodSystem.reliefForNutrition(5));
        assertEquals(160.0D, CitizenFoodSystem.reliefForNutrition(20));
    }
}
