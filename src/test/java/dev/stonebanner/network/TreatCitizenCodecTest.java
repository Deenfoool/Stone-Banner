package dev.stonebanner.network;

import dev.stonebanner.network.packet.TreatCitizenPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TreatCitizenCodecTest {
    @Test void roundTripBothTreatmentKinds() {
        for(boolean splint:new boolean[]{false,true}) {
            var packet=new TreatCitizenPacket(42,UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),splint);
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try { TreatCitizenPacket.encode(packet,buf);assertEquals(packet,TreatCitizenPacket.decode(buf));assertEquals(0,buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
}
