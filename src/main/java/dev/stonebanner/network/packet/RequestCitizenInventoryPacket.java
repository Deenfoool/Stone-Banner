package dev.stonebanner.network.packet;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Requests a one-shot personal-inventory snapshot for the open Citizen inspector. */
public record RequestCitizenInventoryPacket(int entityId) {
    private static final double MAX_VIEW_DISTANCE_SQR = 128.0D * 128.0D;

    public static void encode(RequestCitizenInventoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
    }

    public static RequestCitizenInventoryPacket decode(FriendlyByteBuf buffer) {
        return new RequestCitizenInventoryPacket(buffer.readVarInt());
    }

    public static void handle(RequestCitizenInventoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handleOnServer(sender, packet));
        }
        context.setPacketHandled(true);
    }

    private static void handleOnServer(ServerPlayer sender, RequestCitizenInventoryPacket packet) {
        Entity entity = sender.serverLevel().getEntity(packet.entityId);
        if (!(entity instanceof HumanNpcEntity npc)
                || !npc.isAlive()
                || npc.distanceToSqr(sender) > MAX_VIEW_DISTANCE_SQR) {
            return;
        }
        StoneBannerNetwork.sendCitizenInventorySnapshot(sender, npc.getId(), npc.citizenData().inventory().snapshot());
    }
}
