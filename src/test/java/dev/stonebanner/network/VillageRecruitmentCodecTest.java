package dev.stonebanner.network;

import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageRecruitmentPacket.*;
import dev.stonebanner.village.VillageService.Result;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageRecruitmentCodecTest {
    private FriendlyByteBuf buffer(){return new FriendlyByteBuf(Unpooled.buffer());}
    @Test void snapshotRoundTripsLoadedAndUnloadedEntries(){
        UUID v=UUID.randomUUID();var rows=List.of(new Row(UUID.randomUUID(),"Фермер","farmer",2,new BlockPos(2,70,8),"resident",12,20,Result.OK,Result.PROVISIONS,Result.INVALID),
            new Row(UUID.randomUUID(),"Unavailable","unemployed",0,null,"unavailable",0,0,Result.INVALID,Result.INVALID,Result.INVALID));
        var source=new VillageRecruitmentPacket(ResourceLocation.parse("minecraft:overworld"),List.of(new Village(v,"Village")),v,false,0,2,-20,64,rows,Result.PRICE_CHANGED,true);var b=buffer();
        try{VillageRecruitmentPacket.encode(source,b);assertEquals(source,VillageRecruitmentPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void everyActionRoundTripsIncludingExtremePage(){
        for(var action:VillageRecruitmentActionPacket.Action.values()){
            var source=new VillageRecruitmentActionPacket(action,UUID.randomUUID(),UUID.randomUUID(),true,Integer.MAX_VALUE,8);var b=buffer();
            try{VillageRecruitmentActionPacket.encode(source,b);assertEquals(source,VillageRecruitmentActionPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        }
    }
    @Test void excessiveOrNegativeRowCountsAreRejected(){
        for(int n:new int[]{-1,5}){var b=buffer();try{
            b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));b.writeUUID(UUID.randomUUID());b.writeBoolean(true);b.writeVarInt(0);b.writeVarInt(0);b.writeInt(0);b.writeVarInt(0);b.writeBoolean(false);b.writeBoolean(false);b.writeVarInt(0);b.writeVarInt(n);
            assertThrows(IllegalArgumentException.class,()->VillageRecruitmentPacket.decode(b));
        }finally{b.release();}}
    }
    @Test void listsAreDefensivelyCopiedAndBounded(){
        var villages=new ArrayList<Village>();villages.add(new Village(UUID.randomUUID(),"Village"));var rows=new ArrayList<Row>();
        var p=new VillageRecruitmentPacket(ResourceLocation.parse("minecraft:overworld"),villages,UUID.randomUUID(),true,0,0,0,0,rows,null,false);villages.clear();
        assertEquals(1,p.villages().size());assertThrows(UnsupportedOperationException.class,()->p.rows().clear());
        var row=new Row(UUID.randomUUID(),"NPC","none",1,null,"resident",8,16,Result.OK,Result.OK,Result.INVALID);
        assertThrows(IllegalArgumentException.class,()->new VillageRecruitmentPacket(p.dimension(),p.villages(),p.selected(),false,0,5,0,0,Collections.nCopies(5,row),null,false));
    }
}
