package dev.stonebanner.network;

import dev.stonebanner.network.packet.CitizenTargetPacket;
import dev.stonebanner.network.packet.CitizenWorkTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSequencePacketCodecTest {
    @Test void entityOrdersPreserveAppendAndTarget() {
        var sent=new CitizenTargetPacket(8,29,true);
        var bytes=new FriendlyByteBuf(Unpooled.buffer());
        try { CitizenTargetPacket.encode(sent,bytes);
            assertEquals(sent,CitizenTargetPacket.decode(bytes));
        } finally { bytes.release(); }
    }
    @Test void workOrdersPreserveAppendAndImmutableBlock() {
        var sent=new CitizenWorkTargetPacket(11,new BlockPos(7,-4,99),true);
        var bytes=new FriendlyByteBuf(Unpooled.buffer());
        try { CitizenWorkTargetPacket.encode(sent,bytes);
            assertEquals(sent,CitizenWorkTargetPacket.decode(bytes));
        } finally { bytes.release(); }
    }
    @Test void directCommandsRemainCompatibleWithinProtocol24() {
        var target=new CitizenTargetPacket(5,7,false);
        var work=new CitizenWorkTargetPacket(5,BlockPos.ZERO,false);
        var buf1=new FriendlyByteBuf(Unpooled.buffer());
        var buf2=new FriendlyByteBuf(Unpooled.buffer());
        try {
            CitizenTargetPacket.encode(target,buf1);
            CitizenWorkTargetPacket.encode(work,buf2);
            assertFalse(CitizenTargetPacket.decode(buf1).append());
            assertFalse(CitizenWorkTargetPacket.decode(buf2).append());
        } finally {buf1.release();buf2.release();}
    }
}
