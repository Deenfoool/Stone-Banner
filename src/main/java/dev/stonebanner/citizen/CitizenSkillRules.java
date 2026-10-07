package dev.stonebanner.citizen;

/** Only implemented work has associated practice; cancelled/imaginary jobs grant nothing. */
public final class CitizenSkillRules {
    private CitizenSkillRules(){}
    public static CitizenSkill skillFor(WorkType work){return switch(work){
        case MINING,CLEARING->CitizenSkill.MINING;
        case FORESTRY->CitizenSkill.FORESTRY;
        case BUILDING->CitizenSkill.CONSTRUCTION;
        case FARMING->CitizenSkill.AGRICULTURE;
        case CRAFTING->CitizenSkill.CRAFTING;
        case GUARD->CitizenSkill.COMBAT;
        default->null;
    };}
    public static double workRate(CitizenData data,WorkType work){var skill=skillFor(work);return Math.max(.20,data.health().workEfficiencyMultiplier()*(1+(skill==null?0:data.skill(skill)*.08)));}
    public static double combatRate(CitizenData data){return data.health().combatEfficiencyMultiplier()*(1+data.skill(CitizenSkill.COMBAT)*.05);}
}
