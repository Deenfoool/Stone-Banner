package dev.stonebanner.network;

import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationPlanSnapshotPacketTest {
    @Test
    void roundTripPreservesPlanFrontAndHazardState() {
        ExcavationPlanSnapshotPacket original = new ExcavationPlanSnapshotPacket(List.of(
                new ExcavationPlanSnapshotPacket.PlanSnapshot(
                        17L,
                        new BlockPos(2, 30, -5),
                        new BlockPos(9, 36, 4),
                        1,
                        7,
                        -1,
                        true
                )
        ));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ExcavationPlanSnapshotPacket.encode(original, buffer);
            ExcavationPlanSnapshotPacket decoded = ExcavationPlanSnapshotPacket.decode(buffer);
            assertEquals(original, decoded);
        } finally {
            buffer.release();
        }
    }
}
