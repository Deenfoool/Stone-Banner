package dev.stonebanner.network.packet;

import dev.stonebanner.village.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record VillageJournalActionPacket(Action action, UUID village, VillageData.QuestType quest) {
    public enum Action { OPEN, ACCEPT, SUBMIT }
    public static void encode(VillageJournalActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeUUID(p.village);b.writeEnum(p.quest);}
    public static VillageJournalActionPacket decode(FriendlyByteBuf b){return new VillageJournalActionPacket(b.readEnum(Action.class),b.readUUID(),b.readEnum(VillageData.QuestType.class));}
    public static void handle(VillageJournalActionPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player!=null)VillageJournalService.handle(player,p);});ctx.setPacketHandled(true);
    }
}
