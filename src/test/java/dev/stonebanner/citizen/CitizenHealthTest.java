package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenHealthTest {
    @Test
    void brokenLegReducesMovementAndPersists() {
        CitizenHealth health = new CitizenHealth();
        health.setInjury(BodyPart.LEFT_LEG, InjuryState.FRACTURE);
        double slowed = health.movementMultiplier();

        CompoundTag saved = health.save();
        CitizenHealth loaded = new CitizenHealth();
        loaded.load(saved);

        assertTrue(slowed < 1.0D);
        assertEquals(InjuryState.FRACTURE, loaded.injury(BodyPart.LEFT_LEG));
        assertEquals(slowed, loaded.movementMultiplier(), 0.0001D);
    }

    @Test
    void armInjuryReducesWorkEfficiency() {
        CitizenHealth health = new CitizenHealth();
        health.setInjury(BodyPart.RIGHT_ARM, InjuryState.HEAVY_WOUND);

        assertTrue(health.workEfficiencyMultiplier() < 1.0D);
    }

    @Test
    void severeHeadOrTorsoDamageIsDangerous() {
        CitizenHealth health = new CitizenHealth();
        health.setInjury(BodyPart.TORSO, InjuryState.HEAVY_WOUND);

        assertTrue(health.hasDangerousCoreInjury());
    }
}
