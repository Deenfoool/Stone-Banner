package dev.stonebanner.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenHudCodecTest {
    @Test
    void packsSkillsInjuriesAndPriorities() {
        CitizenData data = new CitizenData();
        data.setSkill(CitizenSkill.MINING, 8);
        data.health().setInjury(BodyPart.LEFT_ARM, InjuryState.FRACTURE);
        data.setWorkPriority(WorkType.MINING, WorkPriority.CRITICAL);
        data.setWorkPriority(WorkType.FORESTRY, WorkPriority.DISABLED);

        assertEquals(8, CitizenHudCodec.skill(CitizenHudCodec.packSkills(data), CitizenSkill.MINING));
        assertEquals(InjuryState.FRACTURE,
                CitizenHudCodec.injury(CitizenHudCodec.packInjuries(data.health()), BodyPart.LEFT_ARM));
        long priorities = CitizenHudCodec.packPriorities(data);
        assertEquals(WorkPriority.CRITICAL, CitizenHudCodec.priority(priorities, WorkType.MINING));
        assertEquals(WorkPriority.DISABLED, CitizenHudCodec.priority(priorities, WorkType.FORESTRY));
    }

    @Test
    void cyclesPriorityOneThroughDisabled() {
        assertEquals(WorkPriority.HIGH, CitizenHudCodec.nextPriority(WorkPriority.CRITICAL));
        assertEquals(WorkPriority.NORMAL, CitizenHudCodec.nextPriority(WorkPriority.HIGH));
        assertEquals(WorkPriority.LOW, CitizenHudCodec.nextPriority(WorkPriority.NORMAL));
        assertEquals(WorkPriority.DISABLED, CitizenHudCodec.nextPriority(WorkPriority.LOW));
        assertEquals(WorkPriority.CRITICAL, CitizenHudCodec.nextPriority(WorkPriority.DISABLED));
    }
}