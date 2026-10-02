package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenNeedsTest {
    @Test
    void clampsAndPersistsNeeds() {
        CitizenNeeds needs = new CitizenNeeds();
        needs.setHunger(120.0D);
        needs.setFatigue(73.0D);
        needs.setDanger(91.0D);

        CompoundTag saved = needs.save();
        CitizenNeeds loaded = new CitizenNeeds();
        loaded.load(saved);

        assertEquals(100.0D, loaded.hunger(), 0.0001D);
        assertEquals(73.0D, loaded.fatigue(), 0.0001D);
        assertEquals(91.0D, loaded.danger(), 0.0001D);
        assertTrue(loaded.isInCriticalDanger());
    }

    @Test
    void sleepingRecoversFatigueWhileHungerStillAdvances() {
        CitizenNeeds needs = new CitizenNeeds();
        needs.setFatigue(80.0D);
        needs.tickSecond(true);

        assertTrue(needs.fatigue() < 80.0D);
        assertTrue(needs.hunger() > 0.0D);
    }
}
