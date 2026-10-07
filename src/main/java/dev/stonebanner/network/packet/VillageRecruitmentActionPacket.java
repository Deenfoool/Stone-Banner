package dev.stonebanner.network.packet;

import dev.stonebanner.village.VillageRecruitmentService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record VillageRecruitmentActionPacket(Action action,UUID village,UUID npc,boolean contracts,int page,int expectedPrice) {
    public enum Action { OPEN, COMPANION, SETTLER, DISMISS }
    public static void encode(VillageRecruitmentActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeUUID(p.village);b.writeUUID(p.npc);b.writeBoolean(p.contracts);b.writeVarInt(p.page);b.writeVarInt(p.expectedPrice);}
    public static VillageRecruitmentActionPacket decode(FriendlyByteBuf b){return new VillageRecruitmentActionPacket(b.readEnum(Action.class),b.readUUID(),b.readUUID(),b.readBoolean(),b.readVarInt(),b.readVarInt());}
    public static void handle(VillageRecruitmentActionPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player!=null)VillageRecruitmentService.handle(player,p);});ctx.setPacketHandled(true);}
}
