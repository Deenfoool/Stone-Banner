package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenJobTest {
    @Test
    void copiesMutableTargetAndClampsRequiredSkill() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(2, 64, -5);
        CitizenJob job = CitizenJob.requiring(
                7L,
                WorkType.MINING,
                mutable,
                CitizenSkill.MINING,
                99,
                20L
        );
        mutable.set(100, 100, 100);

        assertEquals(new BlockPos(2, 64, -5), job.target());
        assertNotSame(mutable, job.target());
        assertEquals(CitizenData.MAX_SKILL_LEVEL, job.minimumSkill());
    }

    @Test
    void rejectsWorkerBelowRequiredSkillAndAcceptsQualifiedWorker() {
        CitizenJob job = CitizenJob.requiring(
                1L,
                WorkType.MINING,
                BlockPos.ZERO,
                CitizenSkill.MINING,
                4,
                0L
        );
        CitizenData citizen = new CitizenData();

        citizen.setSkill(CitizenSkill.MINING, 3);
        assertFalse(job.canBeDoneBy(citizen));

        citizen.setSkill(CitizenSkill.MINING, 4);
        assertTrue(job.canBeDoneBy(citizen));
    }

    @Test
    void disabledWorkPriorityAlwaysRejectsJob() {
        CitizenJob job = CitizenJob.simple(2L, WorkType.HAULING, BlockPos.ZERO, 0L);
        CitizenData citizen = new CitizenData();
        citizen.setWorkPriority(WorkType.HAULING, WorkPriority.DISABLED);

        assertFalse(job.canBeDoneBy(citizen));
    }
}
