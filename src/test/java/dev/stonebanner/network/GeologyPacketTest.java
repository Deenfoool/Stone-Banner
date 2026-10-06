package dev.stonebanner.network;
import dev.stonebanner.geology.*;
import dev.stonebanner.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GeologyPacketTest {
    @Test void allResearchAndSurveyActionsRoundTrip(){for(var action:GeologyService.Action.values()){var p=new GeologyActionPacket(false,new BlockPos(-20,64,30),action,21);var b=new FriendlyByteBuf(Unpooled.buffer());try{GeologyActionPacket.encode(p,b);assertEquals(p,GeologyActionPacket.decode(b));}finally{b.release();}}}
    @Test void layersPreserveKnowledgeAndClaimsWithoutUndergroundCoordinates(){var p=new GeologySnapshotPacket(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),false,BlockPos.ZERO,2,0,List.of(new GeologySnapshotPacket.Tile(-1,0,true,true,2,1,List.of(new GeologyRules.Entry(GeologyRules.Ore.IRON,1)))),List.of(new GeologySnapshotPacket.Claim("Лагерь",0,0,new BlockPos(0,64,0),true,false)));var b=new FriendlyByteBuf(Unpooled.buffer());try{GeologySnapshotPacket.encode(p,b);assertEquals(p,GeologySnapshotPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void researchProgressRoundTripsSeparatelyFromMap(){var p=GeologySnapshotPacket.research(ResourceLocation.fromNamespaceAndPath("minecraft","the_nether"),new BlockPos(2,64,3),3,110);var b=new FriendlyByteBuf(Unpooled.buffer());try{GeologySnapshotPacket.encode(p,b);assertEquals(p,GeologySnapshotPacket.decode(b));}finally{b.release();}}
    @Test void decoderRejectsOversizedTileListBeforeAllocating(){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"));b.writeBoolean(false);b.writeBlockPos(BlockPos.ZERO);b.writeVarInt(0);b.writeVarInt(0);b.writeVarInt(10);assertThrows(IllegalArgumentException.class,()->GeologySnapshotPacket.decode(b));}finally{b.release();}}
}
