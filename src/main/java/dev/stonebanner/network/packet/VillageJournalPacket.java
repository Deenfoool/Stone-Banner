package dev.stonebanner.network.packet;

import dev.stonebanner.client.network.ClientScreenPacketHandlers;
import dev.stonebanner.village.VillageData.QuestType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Bounded, read-only server view; client buttons are hints, never authorization. */
public record VillageJournalPacket(ResourceLocation dimension, List<Entry> villages, String result, boolean opening) {
    public VillageJournalPacket(ResourceLocation dimension,List<Entry> villages,String result){this(dimension,villages,result,false);}
    public VillageJournalPacket { villages=List.copyOf(villages); if(villages.size()>128)throw new IllegalArgumentException("Too many villages"); }
    public enum State { AVAILABLE, ACTIVE, COMPLETED }
    public record Quest(QuestType type, State state, int progress, UUID recipient, String name,
                        BlockPos position, boolean accept, boolean submit) {}
    public record Entry(UUID id, String name, BlockPos center, int population, int trust, String elder, List<Quest> quests) {
        public Entry { quests=List.copyOf(quests);if(quests.size()>4)throw new IllegalArgumentException("Too many quests"); }
    }
    public static void encode(VillageJournalPacket p,FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension);b.writeUtf(p.result,32);b.writeBoolean(p.opening);b.writeVarInt(p.villages.size());
        for(var v:p.villages){
            b.writeUUID(v.id);b.writeUtf(v.name,256);b.writeBlockPos(v.center);b.writeVarInt(v.population);b.writeInt(v.trust);b.writeUtf(v.elder,256);b.writeVarInt(v.quests.size());
            for(var q:v.quests){b.writeEnum(q.type);b.writeEnum(q.state);b.writeVarInt(q.progress);b.writeBoolean(q.recipient!=null);
                if(q.recipient!=null){b.writeUUID(q.recipient);b.writeUtf(q.name,256);b.writeBlockPos(q.position);}
                b.writeBoolean(q.accept);b.writeBoolean(q.submit);
            }
        }
    }
    private static int count(FriendlyByteBuf b,int maximum){int n=b.readVarInt();if(n<0||n>maximum)throw new IllegalArgumentException("Invalid journal length");return n;}
    public static VillageJournalPacket decode(FriendlyByteBuf b) {
        var dimension=b.readResourceLocation();String result=b.readUtf(32);boolean opening=b.readBoolean();var villages=new ArrayList<Entry>();int n=count(b,128);
        for(int i=0;i<n;i++){
            UUID id=b.readUUID();String name=b.readUtf(256);var center=b.readBlockPos();int population=b.readVarInt(),trust=b.readInt();String elder=b.readUtf(256);var quests=new ArrayList<Quest>();int m=count(b,4);
            for(int j=0;j<m;j++){
                var type=b.readEnum(QuestType.class);var state=b.readEnum(State.class);int progress=b.readVarInt();UUID recipient=null;String label="";BlockPos pos=null;
                if(b.readBoolean()){recipient=b.readUUID();label=b.readUtf(256);pos=b.readBlockPos();}
                quests.add(new Quest(type,state,progress,recipient,label,pos,b.readBoolean(),b.readBoolean()));
            }
            villages.add(new Entry(id,name,center,population,trust,elder,quests));
        }
        return new VillageJournalPacket(dimension,villages,result,opening);
    }
    public static void handle(VillageJournalPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientScreenPacketHandlers.journal(p)));ctx.setPacketHandled(true);
    }
}
