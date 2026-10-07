package dev.stonebanner.network.packet;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record RequestCitizenInventoryPacket(int entityId,UUID citizen,ResourceLocation dimension){
    private static final Map<ServerPlayer,Long> LAST_REQUEST=new WeakHashMap<>();
    public static void encode(RequestCitizenInventoryPacket p,FriendlyByteBuf b){b.writeVarInt(p.entityId);b.writeUUID(p.citizen);b.writeResourceLocation(p.dimension);}
    public static RequestCitizenInventoryPacket decode(FriendlyByteBuf b){return new RequestCitizenInventoryPacket(b.readVarInt(),b.readUUID(),b.readResourceLocation());}
    public static boolean allowed(ServerPlayer sender,RequestCitizenInventoryPacket p){
        return sender.isAlive()&&!sender.isSpectator()&&sender.serverLevel().dimension().location().equals(p.dimension)
            &&sender.serverLevel().getEntity(p.entityId) instanceof HumanNpcEntity npc&&npc.isAlive()
            &&npc.getUUID().equals(p.citizen)&&npc.citizenData().canBeViewedBy(sender.getUUID())&&npc.distanceToSqr(sender)<=128*128;
    }
    public static void handle(RequestCitizenInventoryPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player==null||!allowed(player,p))return;
            long now=player.serverLevel().getGameTime();Long previous=LAST_REQUEST.get(player);if(previous!=null&&now>=previous&&now-previous<10)return;LAST_REQUEST.put(player,now);
            var npc=(HumanNpcEntity)player.serverLevel().getEntity(p.entityId);
            StoneBannerNetwork.sendCitizenInventorySnapshot(player,new CitizenInventorySnapshotPacket(npc.getId(),npc.getUUID(),p.dimension,npc.citizenData().save()));
        });ctx.setPacketHandled(true);
    }
}
