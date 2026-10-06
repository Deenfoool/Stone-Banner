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
    void geologistSkillPersistsAndRoutineMiningDoesNotCompeteWithSurveys() {
        var source = new CitizenData(); source.setProfession(CitizenProfession.GEOLOGIST, true);
        source.setSkill(CitizenSkill.GEOLOGY, 8);
        var restored = new CitizenData(); restored.load(source.save());
        assertEquals(CitizenProfession.GEOLOGIST, restored.profession());
        assertEquals(8, CitizenHudCodec.skill(CitizenHudCodec.packSkills(restored), CitizenSkill.GEOLOGY));
        assertEquals(WorkPriority.DISABLED, restored.workPriority(WorkType.MINING));
        assertEquals(WorkPriority.CRITICAL, restored.workPriority(WorkType.EMERGENCY));
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
