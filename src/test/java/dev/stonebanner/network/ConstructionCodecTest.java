package dev.stonebanner.network;

import dev.stonebanner.construction.ConstructionService;
import dev.stonebanner.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionCodecTest {
    private final ResourceLocation dimension=new ResourceLocation("minecraft:overworld");
    @Test void snapshotAndEveryActionRoundTrip(){
        var packet=new ConstructionSnapshotPacket(dimension,true,"created",List.of(new ConstructionSnapshotPacket.Plan(7,new BlockPos(-2,66,9),Rotation.CLOCKWISE_90,false,false,12,"materials")));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            ConstructionSnapshotPacket.encode(packet,b);assertEquals(packet,ConstructionSnapshotPacket.decode(b));assertEquals(0,b.readableBytes());
            for(var action:ConstructionService.Action.values()){
                b.clear();var command=new ConstructionActionPacket(dimension,action,BlockPos.ZERO,3,7);ConstructionActionPacket.encode(command,b);assertEquals(command,ConstructionActionPacket.decode(b));assertEquals(0,b.readableBytes());
            }
        }finally{b.release();}
    }
    @Test void oversizedAndNegativeCountsRejectedBeforeAllocation(){
        for(int count:new int[]{-1,65}){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(dimension);b.writeBoolean(false);b.writeUtf("ready",32);b.writeVarInt(count);assertThrows(IllegalArgumentException.class,()->ConstructionSnapshotPacket.decode(b));}finally{b.release();}}
    }
}
