package dev.stonebanner.network;

import dev.stonebanner.network.packet.ProductionSnapshotPacket;
import dev.stonebanner.production.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionCodecTest {
    private final ResourceLocation dimension=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
    @Test void snapshotRoundTrip(){
        var p=new ProductionSnapshotPacket(dimension,true,List.of(new ProductionSnapshotPacket.Field(1,BlockPos.ZERO,new BlockPos(15,0,15),FarmCrop.CARROTS,false,true)),
            List.of(new ProductionSnapshotPacket.Bill(2,BlockPos.ZERO,ResourceLocation.fromNamespaceAndPath("minecraft","bread"),ProductionData.Mode.MAINTAIN,16,24,"satisfied",true)));
        var buf=new FriendlyByteBuf(Unpooled.buffer());try{ProductionSnapshotPacket.encode(p,buf);assertEquals(p,ProductionSnapshotPacket.decode(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}
    }
    @Test void oversizedFieldLengthRejected(){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(dimension);b.writeBoolean(true);b.writeVarInt(257);assertThrows(IllegalArgumentException.class,()->ProductionSnapshotPacket.decode(b));}finally{b.release();}}
    @Test void negativeBillLengthRejected(){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(dimension);b.writeBoolean(false);b.writeVarInt(0);b.writeVarInt(-1);assertThrows(IllegalArgumentException.class,()->ProductionSnapshotPacket.decode(b));}finally{b.release();}}
    @Test void listsDefensivelyCopied(){var fields=new ArrayList<ProductionSnapshotPacket.Field>();var p=new ProductionSnapshotPacket(dimension,false,fields,List.of());fields.add(new ProductionSnapshotPacket.Field(1,BlockPos.ZERO,BlockPos.ZERO,FarmCrop.WHEAT,false,false));assertTrue(p.fields().isEmpty());assertThrows(UnsupportedOperationException.class,()->p.fields().clear());}
}
