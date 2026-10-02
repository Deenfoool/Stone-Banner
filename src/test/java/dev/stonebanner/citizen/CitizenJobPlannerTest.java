package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenJobPlannerTest {
    @Test
    void higherWorkPriorityWinsEvenWhenFartherAway() {
        CitizenData data = new CitizenData();
        data.setWorkPriority(WorkType.MINING, WorkPriority.HIGH);
        data.setWorkPriority(WorkType.HAULING, WorkPriority.NORMAL);

        CitizenJob hauling = CitizenJob.simple(1L, WorkType.HAULING, new BlockPos(1, 64, 0), 10L);
        CitizenJob mining = CitizenJob.simple(2L, WorkType.MINING, new BlockPos(20, 64, 0), 20L);

        CitizenJob chosen = CitizenJobPlanner.choose(data, new BlockPos(0, 64, 0), List.of(hauling, mining)).orElseThrow();
        assertEquals(2L, chosen.id());
    }

    @Test
    void disabledWorkIsIgnored() {
        CitizenData data = new CitizenData();
        data.setWorkPriority(WorkType.CRAFTING, WorkPriority.DISABLED);

        CitizenJob job = CitizenJob.simple(5L, WorkType.CRAFTING, new BlockPos(1, 64, 0), 0L);
        assertTrue(CitizenJobPlanner.choose(data, BlockPos.ZERO, List.of(job)).isEmpty());
    }

    @Test
    void minimumSkillRequirementIsEnforced() {
        CitizenData data = new CitizenData();
        data.setSkill(CitizenSkill.MINING, 2);
        CitizenJob job = CitizenJob.requiring(
                9L,
                WorkType.MINING,
                new BlockPos(2, 64, 0),
                CitizenSkill.MINING,
                4,
                0L
        );

        assertTrue(CitizenJobPlanner.choose(data, BlockPos.ZERO, List.of(job)).isEmpty());
    }
}
