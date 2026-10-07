package dev.stonebanner.citizen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenPracticeTest {
    @Test void experienceCarriesAcrossLevels(){var d=new CitizenData();d.practice(CitizenSkill.MINING,1000);assertEquals(6,d.skill(CitizenSkill.MINING));assertEquals(25,d.experience(CitizenSkill.MINING));}
    @Test void levelCapAndInvalidAwardsAreSafe(){var d=new CitizenData();d.practice(CitizenSkill.COMBAT,-10);assertEquals(0,d.experience(CitizenSkill.COMBAT));d.practice(CitizenSkill.COMBAT,Integer.MAX_VALUE);assertEquals(10,d.skill(CitizenSkill.COMBAT));assertEquals(0,d.experience(CitizenSkill.COMBAT));}
    @Test void experiencePersistsAndOldWorldsGetZero(){var d=new CitizenData();d.practice(CitizenSkill.FORESTRY,42);var r=new CitizenData();r.load(d.save());assertEquals(42,r.experience(CitizenSkill.FORESTRY));var old=d.save();old.remove("Experience");r.load(old);assertEquals(0,r.experience(CitizenSkill.FORESTRY));}
    @Test void malformedExperienceIsClamped(){var d=new CitizenData();var tag=d.save();tag.getCompound("Experience").putInt("mining",Integer.MAX_VALUE);d.load(tag);assertEquals(d.experienceNeeded(CitizenSkill.MINING)-1,d.experience(CitizenSkill.MINING));}
    @Test void forestrySkillAffectsItsOwnPhysicalWork(){var d=new CitizenData();double mining=CitizenSkillRules.workRate(d,WorkType.MINING);d.setSkill(CitizenSkill.FORESTRY,10);assertEquals(mining,CitizenSkillRules.workRate(d,WorkType.MINING));assertTrue(CitizenSkillRules.workRate(d,WorkType.FORESTRY)>mining);}
    @Test void injuriesReduceCombatWithoutChangingSkill(){var d=new CitizenData();d.setSkill(CitizenSkill.COMBAT,5);double base=CitizenSkillRules.combatRate(d);d.health().setInjury(BodyPart.LEFT_ARM,InjuryState.MISSING);assertTrue(CitizenSkillRules.combatRate(d)<base);assertEquals(5,d.skill(CitizenSkill.COMBAT));}
}
