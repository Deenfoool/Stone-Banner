package dev.stonebanner.network.packet;

import dev.stonebanner.construction.ConstructionService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ConstructionActionPacket(ResourceLocation dimension,ConstructionService.Action action,BlockPos origin,int rotation,long id){
    public ConstructionActionPacket{origin=origin.immutable();}
    public static void encode(ConstructionActionPacket p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeEnum(p.action);b.writeBlockPos(p.origin);b.writeVarInt(p.rotation);b.writeLong(p.id);}
    public static ConstructionActionPacket decode(FriendlyByteBuf b){return new ConstructionActionPacket(b.readResourceLocation(),b.readEnum(ConstructionService.Action.class),b.readBlockPos(),b.readVarInt(),b.readLong());}
    public static void handle(ConstructionActionPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();var player=ctx.getSender();
        if(player!=null)ctx.enqueueWork(()->{if(player.serverLevel().dimension().location().equals(p.dimension))ConstructionService.execute(player,p.action,p.origin,p.rotation,p.id);});
        ctx.setPacketHandled(true);
    }
}
