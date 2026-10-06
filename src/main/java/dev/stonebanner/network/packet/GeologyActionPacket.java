package dev.stonebanner.network.packet;

import dev.stonebanner.geology.GeologyService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record GeologyActionPacket(boolean layers,BlockPos target,GeologyService.Action action,int npcId) {
    public GeologyActionPacket {target=target.immutable();}
    public static void encode(GeologyActionPacket p,FriendlyByteBuf b){b.writeBoolean(p.layers);b.writeBlockPos(p.target);b.writeEnum(p.action);b.writeVarInt(p.npcId);}
    public static GeologyActionPacket decode(FriendlyByteBuf b){return new GeologyActionPacket(b.readBoolean(),b.readBlockPos(),b.readEnum(GeologyService.Action.class),b.readVarInt());}
    public static void handle(GeologyActionPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();var player=ctx.getSender();if(player!=null)ctx.enqueueWork(()->{if(p.layers)GeologyService.layers(player,p.target.getX()>>4,p.target.getZ()>>4);else GeologyService.research(player,p.target,p.action,p.npcId);});ctx.setPacketHandled(true);}
}
