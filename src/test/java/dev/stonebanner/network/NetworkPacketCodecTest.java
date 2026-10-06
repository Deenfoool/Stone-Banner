package dev.stonebanner.network;

import dev.stonebanner.citizen.WorkPriority;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.designation.ExcavationAccessMode;
import dev.stonebanner.network.packet.DesignationAreaPacket;
import dev.stonebanner.network.packet.MoveCitizenPacket;
import dev.stonebanner.network.packet.SetWorkPriorityPacket;
import dev.stonebanner.network.packet.StopCitizenPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkPacketCodecTest {
    @Test
    void moveCitizenPacketRoundTrips() {
        MoveCitizenPacket source = new MoveCitizenPacket(37, new BlockPos(-15, 72, 900));
        FriendlyByteBuf buffer = buffer();

        MoveCitizenPacket.encode(source, buffer);

        assertEquals(source, MoveCitizenPacket.decode(buffer));
    }

    @Test
    void stopCitizenPacketRoundTrips() {
        StopCitizenPacket source = new StopCitizenPacket(8192);
        FriendlyByteBuf buffer = buffer();

        StopCitizenPacket.encode(source, buffer);

        assertEquals(source, StopCitizenPacket.decode(buffer));
    }

    @Test
    void workPriorityPacketRoundTrips() {
        SetWorkPriorityPacket source = new SetWorkPriorityPacket(
                12,
                WorkType.MINING.ordinal(),
                WorkPriority.HIGH.code()
        );
        FriendlyByteBuf buffer = buffer();

        SetWorkPriorityPacket.encode(source, buffer);

        assertEquals(source, SetWorkPriorityPacket.decode(buffer));
    }

    @Test
    void designationPacketRoundTripsWithAccessMode() {
        DesignationAreaPacket source = new DesignationAreaPacket(
                DesignationType.EXCAVATE,
                new BlockPos(-4, 60, -8),
                new BlockPos(9, 75, 11),
                ExcavationAccessMode.LADDERS
        );
        FriendlyByteBuf buffer = buffer();

        DesignationAreaPacket.encode(source, buffer);

        assertEquals(source, DesignationAreaPacket.decode(buffer));
    }

    @Test
    void unknownDesignationAndAccessIdsDecodeSafely() {
        FriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(999);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(999);

        DesignationAreaPacket decoded = DesignationAreaPacket.decode(buffer);
        assertEquals(DesignationType.CANCEL, decoded.type());
        assertEquals(ExcavationAccessMode.AUTO, decoded.accessMode());
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
