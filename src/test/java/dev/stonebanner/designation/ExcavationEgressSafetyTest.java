package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcavationEgressSafetyTest {
    @Test
    void workerCannotMineTheBlockSupportingTheirOwnFeet() {
        BlockPos target = new BlockPos(10, 63, 10);
        BlockPos workerFeet = target.above();

        assertFalse(ExcavationEgressSafety.routeSurvivesRemoval(workerFeet, target, List.of()));
    }

    @Test
    void routeCannotDependOnTheSurfaceThatWillDisappear() {
        BlockPos target = new BlockPos(10, 63, 10);
        BlockPos workerFeet = new BlockPos(8, 64, 10);
        List<BlockPos> route = List.of(
                new BlockPos(9, 64, 10),
                target.above(),
                new BlockPos(11, 64, 10)
        );

        assertFalse(ExcavationEgressSafety.routeSurvivesRemoval(workerFeet, target, route));
        assertTrue(ExcavationEgressSafety.routeSurvivesRemoval(
                workerFeet,
                target,
                List.of(new BlockPos(8, 64, 9), new BlockPos(9, 64, 9), new BlockPos(10, 64, 9))
        ));
    }

    @Test
    void verticalPlanProbesStayOutsideTheQuarryFootprint() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 50, 20,
                14, 60, 24,
                0, ExcavationAccessMode.AUTO.ordinal(), 60, -1
        );

        List<BlockPos> probes = ExcavationEgressSafety.rawEgressProbes(plan);
        assertFalse(probes.isEmpty());
        assertTrue(probes.stream().allMatch(pos ->
                pos.getX() < plan.minX() || pos.getX() > plan.maxX()
                        || pos.getZ() < plan.minZ() || pos.getZ() > plan.maxZ()
        ));
    }

    @Test
    void deepVerticalRampRemainsAContinuousDescendingPerimeterChain() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 5, 20,
                14, 60, 24,
                0, ExcavationAccessMode.AUTO.ordinal(), 5, -1
        );

        BlockPos previous = ExcavationEgressSafety.rampSupportAtY(plan, plan.maxY());
        for (int y = plan.maxY() - 1; y >= plan.minY(); y--) {
            BlockPos current = ExcavationEgressSafety.rampSupportAtY(plan, y);
            assertEquals(1,
                    Math.abs(previous.getX() - current.getX()) + Math.abs(previous.getZ() - current.getZ()));
            assertEquals(previous.getY() - 1, current.getY());
            previous = current;
        }
    }

    @Test
    void oneBlockWideVerticalStripUsesDeterministicLadderColumn() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 30, 20,
                10, 45, 24,
                0, ExcavationAccessMode.AUTO.ordinal(), 40, -1
        );

        assertNull(ExcavationEgressSafety.rampSupportAtY(plan, 40));
        assertEquals(new BlockPos(10, 44, 20), ExcavationEgressSafety.ladderAccessAtY(plan, 44));
        assertEquals(5, ExcavationEgressSafety.requiredLadderCount(plan));
    }

    @Test
    void wideQuarryCanForceLadderAccess() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 30, 20,
                14, 45, 24,
                0, ExcavationAccessMode.LADDERS.ordinal(), 40, -1
        );

        assertTrue(ExcavationEgressSafety.usesLadderAccess(plan));
        assertNull(ExcavationEgressSafety.rampSupportAtY(plan, 40));
        assertEquals(5, ExcavationEgressSafety.requiredLadderCount(plan));
    }

    @Test
    void wideQuarryCanForceRampAccess() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 30, 20,
                14, 45, 24,
                0, ExcavationAccessMode.RAMP.ordinal(), 40, -1
        );

        assertFalse(ExcavationEgressSafety.usesLadderAccess(plan));
        assertNotNull(ExcavationEgressSafety.rampSupportAtY(plan, 40));
        assertEquals(0, ExcavationEgressSafety.requiredLadderCount(plan));
    }

    @Test
    void topVerticalSliceNeedsNoLaddersYet() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                4, 20, 8,
                4, 30, 8,
                0, ExcavationAccessMode.AUTO.ordinal(), 30, -1
        );

        assertEquals(0, ExcavationEgressSafety.requiredLadderCount(plan));
    }

    @Test
    void tunnelProbesUseTheEntranceSideInsteadOfTheDeepEnd() {
        ExcavationPlanData.PlanView positiveX = new ExcavationPlanData.PlanView(
                10, 40, 2,
                30, 42, 4,
                1, ExcavationAccessMode.AUTO.ordinal(), 10, 1
        );
        ExcavationPlanData.PlanView negativeX = new ExcavationPlanData.PlanView(
                10, 40, 2,
                30, 42, 4,
                1, ExcavationAccessMode.AUTO.ordinal(), 30, -1
        );

        assertTrue(ExcavationEgressSafety.rawEgressProbes(positiveX).stream()
                .allMatch(pos -> pos.getX() == positiveX.minX() - 1));
        assertTrue(ExcavationEgressSafety.rawEgressProbes(negativeX).stream()
                .allMatch(pos -> pos.getX() == negativeX.maxX() + 1));
    }
}
