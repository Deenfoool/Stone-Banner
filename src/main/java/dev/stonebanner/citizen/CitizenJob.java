package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Objects;

/** Immutable unit of work published by designations, production, logistics, or other systems. */
public record CitizenJob(
        long id,
        WorkType workType,
        BlockPos target,
        @Nullable CitizenSkill requiredSkill,
        int minimumSkill,
        long createdTick
) {
    public CitizenJob {
        workType = Objects.requireNonNull(workType, "workType");
        target = Objects.requireNonNull(target, "target").immutable();
        minimumSkill = Math.max(0, Math.min(CitizenData.MAX_SKILL_LEVEL, minimumSkill));
    }

    public static CitizenJob simple(long id, WorkType workType, BlockPos target, long createdTick) {
        return new CitizenJob(id, workType, target, null, 0, createdTick);
    }

    public static CitizenJob requiring(long id, WorkType workType, BlockPos target,
                                       CitizenSkill skill, int minimumSkill, long createdTick) {
        return new CitizenJob(id, workType, target, Objects.requireNonNull(skill, "skill"), minimumSkill, createdTick);
    }

    public boolean canBeDoneBy(CitizenData data) {
        if (data.workPriority(workType) == WorkPriority.DISABLED) {
            return false;
        }
        return requiredSkill == null || data.skill(requiredSkill) >= minimumSkill;
    }
}
