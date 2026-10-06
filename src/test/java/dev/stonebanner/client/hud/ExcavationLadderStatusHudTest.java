package dev.stonebanner.client.hud;

import dev.stonebanner.designation.ExcavationAccessMode;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcavationLadderStatusHudTest {
    @Test
    void explicitLaddersEnableStatusForWideQuarry() {
        ExcavationPlanSnapshotPacket.PlanSnapshot plan = vertical(ExcavationAccessMode.LADDERS, 5, 5);
        assertTrue(ExcavationLadderStatusHud.usesLadderAccess(plan));
    }

    @Test
    void explicitRampDisablesLadderStatusForWideQuarry() {
        ExcavationPlanSnapshotPacket.PlanSnapshot plan = vertical(ExcavationAccessMode.RAMP, 5, 5);
        assertFalse(ExcavationLadderStatusHud.usesLadderAccess(plan));
    }

    @Test
    void autoStillFallsBackToLaddersForNarrowShaft() {
        ExcavationPlanSnapshotPacket.PlanSnapshot plan = vertical(ExcavationAccessMode.AUTO, 1, 4);
        assertTrue(ExcavationLadderStatusHud.usesLadderAccess(plan));
    }

    private static ExcavationPlanSnapshotPacket.PlanSnapshot vertical(ExcavationAccessMode mode, int sizeX, int sizeZ) {
        return new ExcavationPlanSnapshotPacket.PlanSnapshot(
                4L,
                new BlockPos(10, 20, 30),
                new BlockPos(10 + sizeX - 1, 35, 30 + sizeZ - 1),
                0,
                mode.ordinal(),
                35,
                -1,
                false
        );
    }
}
