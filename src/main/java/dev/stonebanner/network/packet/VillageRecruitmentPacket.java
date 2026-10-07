package dev.stonebanner.network.packet;

import dev.stonebanner.client.network.ClientScreenPacketHandlers;
import dev.stonebanner.village.VillageService.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Four entries per server page; no client/entity objects or foreign contract owners. */
public record VillageRecruitmentPacket(ResourceLocation dimension,List<Village> villages,UUID selected,
        boolean contracts,int page,int total,int trust,int emeralds,List<Row> rows,Result result,boolean opening) {
    public static final int PAGE_SIZE=4;
    public VillageRecruitmentPacket {villages=List.copyOf(villages);rows=List.copyOf(rows);if(villages.size()>128||rows.size()>PAGE_SIZE)throw new IllegalArgumentException("Oversized recruitment snapshot");}
    public record Village(UUID id,String name) {}
    public record Row(UUID id,String name,String profession,int level,BlockPos position,String status,
                      int companionPrice,int settlerPrice,Result companion,Result settler,Result dismiss) {}
    public static void encode(VillageRecruitmentPacket p,FriendlyByteBuf b){
        b.writeResourceLocation(p.dimension);b.writeUUID(p.selected);b.writeBoolean(p.contracts);b.writeVarInt(p.page);b.writeVarInt(p.total);b.writeInt(p.trust);b.writeVarInt(p.emeralds);b.writeBoolean(p.opening);
        b.writeBoolean(p.result!=null);if(p.result!=null)b.writeEnum(p.result);
        b.writeVarInt(p.villages.size());for(var v:p.villages){b.writeUUID(v.id);b.writeUtf(v.name,256);}
        b.writeVarInt(p.rows.size());for(var r:p.rows){b.writeUUID(r.id);b.writeUtf(r.name,256);b.writeUtf(r.profession,128);b.writeVarInt(r.level);b.writeBoolean(r.position!=null);if(r.position!=null)b.writeBlockPos(r.position);
            b.writeUtf(r.status,32);b.writeVarInt(r.companionPrice);b.writeVarInt(r.settlerPrice);b.writeEnum(r.companion);b.writeEnum(r.settler);b.writeEnum(r.dismiss);}
    }
    private static int count(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid recruitment length");return n;}
    public static VillageRecruitmentPacket decode(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();var selected=b.readUUID();boolean contracts=b.readBoolean();int page=b.readVarInt(),total=b.readVarInt(),trust=b.readInt(),emeralds=b.readVarInt();boolean opening=b.readBoolean();Result result=b.readBoolean()?b.readEnum(Result.class):null;
        var villages=new ArrayList<Village>();int n=count(b,128);for(int i=0;i<n;i++)villages.add(new Village(b.readUUID(),b.readUtf(256)));
        var rows=new ArrayList<Row>();n=count(b,PAGE_SIZE);for(int i=0;i<n;i++){
            UUID id=b.readUUID();String name=b.readUtf(256),profession=b.readUtf(128);int level=b.readVarInt();BlockPos pos=b.readBoolean()?b.readBlockPos():null;String status=b.readUtf(32);
            rows.add(new Row(id,name,profession,level,pos,status,b.readVarInt(),b.readVarInt(),b.readEnum(Result.class),b.readEnum(Result.class),b.readEnum(Result.class)));
        }
        return new VillageRecruitmentPacket(dimension,villages,selected,contracts,page,total,trust,emeralds,rows,result,opening);
    }
    public static void handle(VillageRecruitmentPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientScreenPacketHandlers.recruitment(p)));ctx.setPacketHandled(true);}
}
