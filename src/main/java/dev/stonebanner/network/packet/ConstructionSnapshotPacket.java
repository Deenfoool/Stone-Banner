package dev.stonebanner.network.packet;

import dev.stonebanner.construction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record ConstructionSnapshotPacket(ResourceLocation dimension,boolean opening,String result,List<Plan> plans){
    public record Plan(long id,BlockPos origin,Rotation rotation,boolean paused,boolean completed,int done,String status){}
    public ConstructionSnapshotPacket{plans=List.copyOf(plans);if(plans.size()>ConstructionData.LIMIT)throw new IllegalArgumentException("Construction snapshot too large");}
    public static ConstructionSnapshotPacket forPlayer(ServerPlayer player,boolean opening,String result){
        var level=player.serverLevel();
        return new ConstructionSnapshotPacket(level.dimension().location(),opening,result,ConstructionData.forLevel(level).plans().stream().filter(p->p.owner.equals(player.getUUID()))
            .map(p->new Plan(p.id,p.origin,p.rotation,p.paused,p.completed,(int)CottageBlueprint.placements().stream().filter(v->v.matches(level,p.origin,p.rotation)).count(),p.status)).toList());
    }
    public static void encode(ConstructionSnapshotPacket p,FriendlyByteBuf b){
        b.writeResourceLocation(p.dimension);b.writeBoolean(p.opening);b.writeUtf(p.result,32);b.writeVarInt(p.plans.size());
        for(var v:p.plans){b.writeLong(v.id);b.writeBlockPos(v.origin);b.writeEnum(v.rotation);b.writeBoolean(v.paused);b.writeBoolean(v.completed);b.writeVarInt(v.done);b.writeUtf(v.status,32);}
    }
    public static ConstructionSnapshotPacket decode(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();boolean opening=b.readBoolean();String result=b.readUtf(32);int count=b.readVarInt();
        if(count<0||count>ConstructionData.LIMIT)throw new IllegalArgumentException("Invalid construction count");
        var plans=new ArrayList<Plan>();for(int i=0;i<count;i++)plans.add(new Plan(b.readLong(),b.readBlockPos(),b.readEnum(Rotation.class),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readUtf(32)));
        return new ConstructionSnapshotPacket(dimension,opening,result,plans);
    }
    public static void handle(ConstructionSnapshotPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.stonebanner.client.network.ClientScreenPacketHandlers.construction(p)));ctx.setPacketHandled(true);
    }
}
