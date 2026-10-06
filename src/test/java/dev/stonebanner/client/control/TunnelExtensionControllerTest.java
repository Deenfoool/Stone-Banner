package dev.stonebanner.client.control;

import dev.stonebanner.designation.ExcavationAccessMode;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TunnelExtensionControllerTest {
    @Test
    void positiveXTunnelExtendsOnlyPastDeepEnd() {
        ExcavationPlanSnapshotPacket.PlanSnapshot plan = new ExcavationPlanSnapshotPacket.PlanSnapshot(
                1L,
                new BlockPos(10, 40, 2),
                new BlockPos(30, 42, 4),
                1,
                ExcavationAccessMode.AUTO.ordinal(),
                18,
                1,
                false
        );

        assertEquals(12, TunnelExtensionController.extensionLength(plan, new BlockPos(42, 41, 99)));
        assertEquals(0, TunnelExtensionController.extensionLength(plan, new BlockPos(30, 41, 3)));
        assertEquals(-5, TunnelExtensionController.extensionLength(plan, new BlockPos(25, 41, 3)));
    }

    @Test
    void negativeZTunnelExtendsOnlyPastDeepEnd() {
        ExcavationPlanSnapshotPacket.PlanSnapshot plan = new ExcavationPlanSnapshotPacket.PlanSnapshot(
                2L,
                new BlockPos(6, 20, -30),
                new BlockPos(8, 22, -10),
                2,
                ExcavationAccessMode.AUTO.ordinal(),
                -18,
                -1,
                false
        );

        assertEquals(9, TunnelExtensionController.extensionLength(plan, new BlockPos(100, 21, -39)));
        assertEquals(0, TunnelExtensionController.extensionLength(plan, new BlockPos(7, 21, -30)));
        assertEquals(-4, TunnelExtensionController.extensionLength(plan, new BlockPos(7, 21, -26)));
    }
}
