package dev.stonebanner.network.packet;

import dev.stonebanner.production.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Only the requesting player's plans; bounded snapshot, not client authority. */
public record ProductionSnapshotPacket(ResourceLocation dimension, boolean opening, List<Field> fields, List<Bill> bills){
    public record Field(long id,BlockPos min,BlockPos max,FarmCrop crop,boolean paused,boolean fertilize){}
    public record Bill(long id,BlockPos station,ResourceLocation recipe,ProductionData.Mode mode,int amount,int made,String status,boolean paused){}
    public ProductionSnapshotPacket{fields=List.copyOf(fields);bills=List.copyOf(bills);if(fields.size()>256||bills.size()>512)throw new IllegalArgumentException("Production snapshot too large");}
    public static ProductionSnapshotPacket forPlayer(ServerPlayer p,boolean opening){
        var d=ProductionData.forLevel(p.serverLevel());
        return new ProductionSnapshotPacket(p.level().dimension().location(),opening,
            d.fields().stream().filter(f->f.owner.equals(p.getUUID())).map(f->new Field(f.id,f.min,f.max,f.crop,f.paused,f.fertilize)).toList(),
            d.bills().stream().filter(b->b.owner.equals(p.getUUID())).map(b->new Bill(b.id,b.station,b.recipe,b.mode,b.amount,b.made,b.status,b.paused)).toList());
    }
    public static void encode(ProductionSnapshotPacket p,FriendlyByteBuf b){
        b.writeResourceLocation(p.dimension);b.writeBoolean(p.opening);b.writeVarInt(p.fields.size());
        for(var f:p.fields){b.writeLong(f.id);b.writeBlockPos(f.min);b.writeBlockPos(f.max);b.writeEnum(f.crop);b.writeBoolean(f.paused);b.writeBoolean(f.fertilize);}
        b.writeVarInt(p.bills.size());for(var v:p.bills){b.writeLong(v.id);b.writeBlockPos(v.station);b.writeResourceLocation(v.recipe);b.writeEnum(v.mode);b.writeVarInt(v.amount);b.writeVarInt(v.made);b.writeUtf(v.status,32);b.writeBoolean(v.paused);}
    }
    private static int length(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid production count");return n;}
    public static ProductionSnapshotPacket decode(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();boolean opening=b.readBoolean();var fields=new ArrayList<Field>();var bills=new ArrayList<Bill>();int n=length(b,256);
        for(int i=0;i<n;i++)fields.add(new Field(b.readLong(),b.readBlockPos(),b.readBlockPos(),b.readEnum(FarmCrop.class),b.readBoolean(),b.readBoolean()));
        n=length(b,512);for(int i=0;i<n;i++)bills.add(new Bill(b.readLong(),b.readBlockPos(),b.readResourceLocation(),b.readEnum(ProductionData.Mode.class),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readBoolean()));
        return new ProductionSnapshotPacket(dimension,opening,fields,bills);
    }
    public static void handle(ProductionSnapshotPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.stonebanner.client.network.ClientScreenPacketHandlers.production(p)));ctx.setPacketHandled(true);
    }
}
