package dev.stonebanner.citizen;

/** Compact encoding used only to mirror persistent CitizenData to client HUDs. */
public final class CitizenHudCodec {
    private static final int SKILL_BITS = 4;
    private static final int INJURY_BITS = 3;
    private static final int PRIORITY_BITS = 3;

    private CitizenHudCodec() {
    }

    public static int packSkills(CitizenData data) {
        int packed = 0;
        for (CitizenSkill skill : CitizenSkill.values()) {
            packed |= (data.skill(skill) & 0xF) << (skill.ordinal() * SKILL_BITS);
        }
        return packed;
    }

    public static int skill(int packed, CitizenSkill skill) {
        return (packed >>> (skill.ordinal() * SKILL_BITS)) & 0xF;
    }

    public static int packInjuries(CitizenHealth health) {
        int packed = 0;
        for (BodyPart part : BodyPart.values()) {
            packed |= (health.injury(part).ordinal() & 0x7) << (part.ordinal() * INJURY_BITS);
        }
        return packed;
    }

    public static InjuryState injury(int packed, BodyPart part) {
        int id = (packed >>> (part.ordinal() * INJURY_BITS)) & 0x7;
        InjuryState[] values = InjuryState.values();
        return id >= 0 && id < values.length ? values[id] : InjuryState.NORMAL;
    }

    public static long packPriorities(CitizenData data) {
        long packed = 0L;
        for (WorkType type : WorkType.values()) {
            int code = encodePriority(data.workPriority(type));
            packed |= ((long) code & 0x7L) << (type.ordinal() * PRIORITY_BITS);
        }
        return packed;
    }

    public static WorkPriority priority(long packed, WorkType type) {
        int code = (int) ((packed >>> (type.ordinal() * PRIORITY_BITS)) & 0x7L);
        return decodePriority(code);
    }

    public static int encodePriority(WorkPriority priority) {
        return priority == WorkPriority.DISABLED ? 0 : priority.code();
    }

    public static WorkPriority decodePriority(int code) {
        return code == 0 ? WorkPriority.DISABLED : WorkPriority.fromCode(code);
    }

    public static WorkPriority nextPriority(WorkPriority current) {
        return switch (current) {
            case CRITICAL -> WorkPriority.HIGH;
            case HIGH -> WorkPriority.NORMAL;
            case NORMAL -> WorkPriority.LOW;
            case LOW -> WorkPriority.DISABLED;
            case DISABLED -> WorkPriority.CRITICAL;
        };
    }
}