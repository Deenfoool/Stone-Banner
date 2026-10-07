package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Persistent gameplay data owned by one Citizen, independent from its visual entity type. */
public final class CitizenData {
    public static final int MAX_SKILL_LEVEL = 10;

    private static final String TAG_PROFESSION = "Profession";
    private static final String TAG_PARTICIPATION = "Participation";
    private static final String TAG_SKILLS_INITIALIZED = "SkillsInitialized";
    private static final String TAG_SKILLS = "Skills";
    private static final String TAG_WORK_PRIORITIES = "WorkPriorities";
    private static final String TAG_NEEDS = "Needs";
    private static final String TAG_HEALTH = "Health";
    private static final String TAG_HOME = "Home";
    private static final String TAG_INVENTORY = "Inventory";

    private CitizenProfession profession = CitizenProfession.UNEMPLOYED;
    private CitizenParticipation participation = CitizenParticipation.LOCAL_HELPER;
    private final EnumMap<CitizenSkill, Integer> skills = new EnumMap<>(CitizenSkill.class);
    private final EnumMap<CitizenSkill, Integer> experience = new EnumMap<>(CitizenSkill.class);
    private final EnumMap<WorkType, WorkPriority> workPriorities = new EnumMap<>(WorkType.class);
    private final CitizenNeeds needs = new CitizenNeeds();
    private final CitizenHealth health = new CitizenHealth();
    private final CitizenHome home = new CitizenHome();
    private final CitizenInventory inventory = new CitizenInventory();
    private boolean skillsInitialized;
    private java.util.UUID recruitedBy;
    public void setRecruitedBy(java.util.UUID player) { recruitedBy = player; }
    public boolean canBeViewedBy(java.util.UUID player) { return recruitedBy==null||recruitedBy.equals(player); }
    public java.util.Optional<java.util.UUID> recruitedBy(){return java.util.Optional.ofNullable(recruitedBy);}
    private boolean returningToVillage;
    public boolean returningToVillage() { return returningToVillage; }
    public void setReturningToVillage(boolean returning) { returningToVillage = returning; }
    public boolean canBeDirectedBy(java.util.UUID player) { return !returningToVillage && (recruitedBy == null || recruitedBy.equals(player)); }

    public CitizenData() {
        for (CitizenSkill skill : CitizenSkill.values()) {
            skills.put(skill, 0);
        }
        applyProfessionDefaults();
    }

    public CitizenProfession profession() {
        return profession;
    }

    public void setProfession(CitizenProfession profession, boolean resetPriorities) {
        this.profession = profession == null ? CitizenProfession.UNEMPLOYED : profession;
        if (resetPriorities) {
            applyProfessionDefaults();
        }
    }

    public CitizenParticipation participation() {
        return participation;
    }

    public void setParticipation(CitizenParticipation participation) {
        this.participation = participation == null ? CitizenParticipation.LOCAL_HELPER : participation;
    }

    public CitizenNeeds needs() {
        return needs;
    }

    public CitizenHealth health() {
        return health;
    }

    public CitizenHome home() {
        return home;
    }

    public CitizenInventory inventory() {
        return inventory;
    }

    public boolean canTravelTo(net.minecraft.core.BlockPos target) {
        return participation != CitizenParticipation.LOCAL_HELPER || !home.hasHome() || home.contains(target);
    }

    public int skill(CitizenSkill skill) {
        return skills.getOrDefault(skill, 0);
    }

    public void setSkill(CitizenSkill skill, int level) {
        if (skill == null) {
            return;
        }
        skills.put(skill, Math.max(0, Math.min(MAX_SKILL_LEVEL, level)));
    }

    public Map<CitizenSkill, Integer> skillsView() {
        return Map.copyOf(skills);
    }
    public int experience(CitizenSkill skill){return experience.getOrDefault(skill,0);}
    public int experienceNeeded(CitizenSkill skill){return 100+skill(skill)*25;}
    public void practice(CitizenSkill skill,int amount){
        if(skill==null||amount<=0||skill(skill)>=MAX_SKILL_LEVEL)return;
        long xp=(long)experience(skill)+amount;
        while(skill(skill)<MAX_SKILL_LEVEL&&xp>=experienceNeeded(skill)){xp-=experienceNeeded(skill);setSkill(skill,skill(skill)+1);}
        experience.put(skill,skill(skill)==MAX_SKILL_LEVEL?0:(int)xp);
    }

    public WorkPriority workPriority(WorkType workType) {
        return workPriorities.getOrDefault(workType, WorkPriority.NORMAL);
    }

    public void setWorkPriority(WorkType workType, WorkPriority priority) {
        if (workType != null && priority != null) {
            workPriorities.put(workType, priority);
        }
    }

    public Map<WorkType, WorkPriority> workPrioritiesView() {
        return Map.copyOf(workPriorities);
    }

    /** Deterministic starter variation: same saved citizen seed always produces the same initial skills. */
    public void initializeStarterSkills(int citizenSeed) {
        if (skillsInitialized) {
            return;
        }

        Random random = new Random(Integer.toUnsignedLong(citizenSeed) ^ 0x53A9B4C2D17E8F01L);
        for (CitizenSkill skill : CitizenSkill.values()) {
            setSkill(skill, random.nextInt(5));
        }
        skillsInitialized = true;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        if (recruitedBy != null) root.putUUID("RecruitedBy", recruitedBy);
        root.putBoolean("ReturningToVillage", returningToVillage);
        root.putString(TAG_PROFESSION, profession.serializedName());
        root.putString(TAG_PARTICIPATION, participation.serializedName());
        root.putBoolean(TAG_SKILLS_INITIALIZED, skillsInitialized);

        CompoundTag skillTag = new CompoundTag();
        for (CitizenSkill skill : CitizenSkill.values()) {
            skillTag.putInt(skill.serializedName(), skill(skill));
        }
        root.put(TAG_SKILLS, skillTag);
        var xpTag=new CompoundTag();for(var skill:CitizenSkill.values())xpTag.putInt(skill.serializedName(),experience(skill));root.put("Experience",xpTag);

        CompoundTag priorityTag = new CompoundTag();
        for (WorkType workType : WorkType.values()) {
            priorityTag.putInt(workType.serializedName(), workPriority(workType).code());
        }
        root.put(TAG_WORK_PRIORITIES, priorityTag);
        root.put(TAG_NEEDS, needs.save());
        root.put(TAG_HEALTH, health.save());
        root.put(TAG_HOME, home.save());
        root.put(TAG_INVENTORY, inventory.save());
        return root;
    }

    public void load(CompoundTag root) {
        recruitedBy = root.hasUUID("RecruitedBy") ? root.getUUID("RecruitedBy") : null;
        returningToVillage = root.getBoolean("ReturningToVillage");
        profession = CitizenProfession.fromSerializedName(root.getString(TAG_PROFESSION));
        participation = CitizenParticipation.fromSerializedName(root.getString(TAG_PARTICIPATION));
        applyProfessionDefaults();

        if (root.contains(TAG_SKILLS, Tag.TAG_COMPOUND)) {
            CompoundTag skillTag = root.getCompound(TAG_SKILLS);
            for (CitizenSkill skill : CitizenSkill.values()) {
                if (skillTag.contains(skill.serializedName(), Tag.TAG_INT)) {
                    setSkill(skill, skillTag.getInt(skill.serializedName()));
                }
            }
        }
        skillsInitialized = root.getBoolean(TAG_SKILLS_INITIALIZED) || root.contains(TAG_SKILLS, Tag.TAG_COMPOUND);
        experience.clear();var xpTag=root.getCompound("Experience");for(var skill:CitizenSkill.values()){
            experience.put(skill,skill(skill)>=MAX_SKILL_LEVEL?0:Math.max(0,Math.min(experienceNeeded(skill)-1,xpTag.getInt(skill.serializedName()))));
        }

        if (root.contains(TAG_WORK_PRIORITIES, Tag.TAG_COMPOUND)) {
            CompoundTag priorityTag = root.getCompound(TAG_WORK_PRIORITIES);
            for (WorkType workType : WorkType.values()) {
                if (priorityTag.contains(workType.serializedName(), Tag.TAG_INT)) {
                    setWorkPriority(workType, WorkPriority.fromCode(priorityTag.getInt(workType.serializedName())));
                }
            }
        }

        if (root.contains(TAG_NEEDS, Tag.TAG_COMPOUND)) {
            needs.load(root.getCompound(TAG_NEEDS));
        }
        if (root.contains(TAG_HEALTH, Tag.TAG_COMPOUND)) {
            health.load(root.getCompound(TAG_HEALTH));
        }
        if (root.contains(TAG_HOME, Tag.TAG_COMPOUND)) {
            home.load(root.getCompound(TAG_HOME));
        }
        if (root.contains(TAG_INVENTORY, Tag.TAG_COMPOUND)) {
            inventory.load(root.getCompound(TAG_INVENTORY));
        } else {
            inventory.clear();
        }
    }

    private void applyProfessionDefaults() {
        workPriorities.clear();
        for (WorkType workType : WorkType.values()) {
            workPriorities.put(workType, WorkPriority.NORMAL);
        }

        workPriorities.put(WorkType.EMERGENCY, WorkPriority.CRITICAL);
        workPriorities.put(WorkType.TREATMENT, WorkPriority.HIGH);

        switch (profession) {
            case FARMER -> workPriorities.put(WorkType.FARMING, WorkPriority.HIGH);
            case LUMBERJACK -> workPriorities.put(WorkType.FORESTRY, WorkPriority.HIGH);
            case MINER -> workPriorities.put(WorkType.MINING, WorkPriority.HIGH);
            case BUILDER -> {
                workPriorities.put(WorkType.BUILDING, WorkPriority.HIGH);
                workPriorities.put(WorkType.HAULING, WorkPriority.HIGH);
            }
            case CRAFTSMAN -> workPriorities.put(WorkType.CRAFTING, WorkPriority.HIGH);
            case TRADER -> workPriorities.put(WorkType.TRADING, WorkPriority.HIGH);
            case GUARD -> workPriorities.put(WorkType.GUARD, WorkPriority.HIGH);
            case GEOLOGIST -> {
                for (WorkType type : WorkType.values()) {
                    if (type != WorkType.EMERGENCY && type != WorkType.TREATMENT)
                        workPriorities.put(type, WorkPriority.DISABLED);
                }
            }
            case UNEMPLOYED -> workPriorities.put(WorkType.HAULING, WorkPriority.HIGH);
        }
    }
}
