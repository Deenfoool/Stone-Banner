package dev.stonebanner.network.packet;

import dev.stonebanner.citizen.CitizenMedicalService;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record TreatCitizenPacket(int entityId, UUID citizen, ResourceLocation dimension, boolean splint) {
    public static void encode(TreatCitizenPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entityId); b.writeUUID(p.citizen); b.writeResourceLocation(p.dimension); b.writeBoolean(p.splint);
    }
    public static TreatCitizenPacket decode(FriendlyByteBuf b) {
        return new TreatCitizenPacket(b.readVarInt(), b.readUUID(), b.readResourceLocation(), b.readBoolean());
    }
    public static boolean apply(ServerPlayer player, TreatCitizenPacket p) {
        return player != null && player.level().dimension().location().equals(p.dimension)
                && player.level().getEntity(p.entityId) instanceof HumanNpcEntity npc && npc.getUUID().equals(p.citizen)
                && CitizenMedicalService.fromInventory(player, npc, p.splint);
    }
    public static void handle(TreatCitizenPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && !apply(player, p)) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("medical.stonebanner.rejected"), true);
        });
        context.setPacketHandled(true);
    }
}
