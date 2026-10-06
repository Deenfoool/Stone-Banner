package dev.stonebanner.network;
import dev.stonebanner.network.packet.CitizenTargetPacket;
import dev.stonebanner.network.packet.CitizenWorkTargetPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TacticalOrdersPacketTest {
    @Test void targetOrderRoundTrip(){var b=new FriendlyByteBuf(Unpooled.buffer());try{var p=new CitizenTargetPacket(512,1234);CitizenTargetPacket.encode(p,b);assertEquals(p,CitizenTargetPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void workOrderCopiesMutableTargetAndRoundTrips(){var pos=new BlockPos.MutableBlockPos(-15,80,257);var p=new CitizenWorkTargetPacket(98,pos);pos.set(0,0,0);assertEquals(new BlockPos(-15,80,257),p.target());var b=new FriendlyByteBuf(Unpooled.buffer());try{CitizenWorkTargetPacket.encode(p,b);assertEquals(p,CitizenWorkTargetPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
}
