package dev.stonebanner.network;

import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageJournalPacket.*;
import dev.stonebanner.village.VillageData.QuestType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageJournalCodecTest {
    private FriendlyByteBuf buffer(){return new FriendlyByteBuf(Unpooled.buffer());}
    @Test void snapshotRoundTripsWithMissingAndPresentRecipients(){
        var quests=List.of(new Quest(QuestType.FOOD,State.ACTIVE,11,UUID.randomUUID(),"Фермер",new BlockPos(-3,70,8),false,false),
            new Quest(QuestType.DEFENCE,State.COMPLETED,3,null,"",null,false,false));
        var packet=new VillageJournalPacket(ResourceLocation.parse("minecraft:overworld"),List.of(new Entry(UUID.randomUUID(),"Деревня",BlockPos.ZERO,5,-20,"Староста",quests)),"items",true);
        var b=buffer();try{VillageJournalPacket.encode(packet,b);assertEquals(packet,VillageJournalPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void actionRoundTripsAllModes(){
        for(var action:VillageJournalActionPacket.Action.values()){
            var p=new VillageJournalActionPacket(action,UUID.randomUUID(),QuestType.TIMBER);var b=buffer();
            try{VillageJournalActionPacket.encode(p,b);assertEquals(p,VillageJournalActionPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        }
    }
    @Test void excessiveOrNegativeVillageCountsAreRejected(){
        for(int n:new int[]{-1,129}){var b=buffer();try{
            b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));b.writeUtf("");b.writeBoolean(false);b.writeVarInt(n);
            assertThrows(IllegalArgumentException.class,()->VillageJournalPacket.decode(b));
        }finally{b.release();}}
    }
    @Test void excessiveQuestCountIsRejectedBeforeAllocatingRows(){
        var b=buffer();try{
            b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));b.writeUtf("");b.writeBoolean(false);b.writeVarInt(1);
            b.writeUUID(UUID.randomUUID());b.writeUtf("Village");b.writeBlockPos(BlockPos.ZERO);b.writeVarInt(5);b.writeInt(0);b.writeUtf("");b.writeVarInt(5);
            assertThrows(IllegalArgumentException.class,()->VillageJournalPacket.decode(b));
        }finally{b.release();}
    }
    @Test void snapshotsDoNotRetainMutableLists(){
        var rows=new ArrayList<Quest>();var entry=new Entry(UUID.randomUUID(),"Village",BlockPos.ZERO,3,0,"",rows);rows.add(new Quest(QuestType.IRON,State.AVAILABLE,0,null,"",null,true,false));
        var villages=new ArrayList<Entry>();villages.add(entry);var p=new VillageJournalPacket(ResourceLocation.parse("minecraft:overworld"),villages,"");villages.clear();
        assertEquals(1,p.villages().size());assertEquals(0,p.villages().get(0).quests().size());
        assertThrows(UnsupportedOperationException.class,()->p.villages().clear());
    }
}
