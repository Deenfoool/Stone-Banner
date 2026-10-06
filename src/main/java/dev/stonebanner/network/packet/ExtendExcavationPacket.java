package dev.stonebanner.network.packet;

import dev.stonebanner.designation.ExcavationPlanData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serverbound request to extend the deep end of one active tunnel plan. */
public record ExtendExcavationPacket(long planId, int additionalLength) {
    public static void encode(ExtendExcavationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.planId);
        buffer.writeVarInt(packet.additionalLength);
    }

    public static ExtendExcavationPacket decode(FriendlyByteBuf buffer) {
        return new ExtendExcavationPacket(buffer.readVarLong(), buffer.readVarInt());
    }

    public static void handle(ExtendExcavationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                if (packet.planId <= 0L || packet.additionalLength <= 0 || packet.additionalLength > 64) {
                    sender.displayClientMessage(
                            Component.translatable("message.stonebanner.excavation.extend.invalid"),
                            true
                    );
                    return;
                }
                ExcavationPlanData.ExtensionResult result = ExcavationPlanData.forLevel(sender.serverLevel())
                        .extendTunnel(sender.serverLevel(), packet.planId, packet.additionalLength);
                Component feedback = switch (result.status()) {
                    case EXTENDED -> Component.translatable(
                            "message.stonebanner.excavation.extend.success",
                            packet.additionalLength,
                            result.addedTargets()
                    );
                    case NOT_FOUND -> Component.translatable("message.stonebanner.excavation.extend.not_found");
                    case NOT_TUNNEL -> Component.translatable("message.stonebanner.excavation.extend.not_tunnel");
                    case INVALID_LENGTH -> Component.translatable("message.stonebanner.excavation.extend.invalid");
                    case TOO_LARGE -> Component.translatable("message.stonebanner.excavation.extend.too_large");
                };
                sender.displayClientMessage(feedback, true);
            });
        }
        context.setPacketHandled(true);
    }
}
