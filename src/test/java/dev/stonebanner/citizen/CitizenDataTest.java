package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenDataTest {
    @Test
    void starterSkillsAreDeterministicForSameSeed() {
        CitizenData first = new CitizenData();
        CitizenData second = new CitizenData();

        first.initializeStarterSkills(12345);
        second.initializeStarterSkills(12345);

        assertEquals(first.skillsView(), second.skillsView());
    }

    @Test
    void professionAppliesUsefulDefaultPriorities() {
        CitizenData data = new CitizenData();
        data.setProfession(CitizenProfession.MINER, true);

        assertEquals(WorkPriority.CRITICAL, data.workPriority(WorkType.EMERGENCY));
        assertEquals(WorkPriority.HIGH, data.workPriority(WorkType.MINING));
    }

    @Test
    void nbtRoundTripPreservesCitizenConfiguration() {
        CitizenData source = new CitizenData();
        source.initializeStarterSkills(77);
        source.setProfession(CitizenProfession.CRAFTSMAN, true);
        source.setSkill(CitizenSkill.CRAFTING, 9);
        source.setWorkPriority(WorkType.HAULING, WorkPriority.LOW);

        CompoundTag saved = source.save();
        CitizenData restored = new CitizenData();
        restored.load(saved);

        assertEquals(CitizenProfession.CRAFTSMAN, restored.profession());
        assertEquals(9, restored.skill(CitizenSkill.CRAFTING));
        assertEquals(WorkPriority.LOW, restored.workPriority(WorkType.HAULING));
    }
}
