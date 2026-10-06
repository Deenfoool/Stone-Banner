package dev.stonebanner.network.packet;

import dev.stonebanner.designation.DesignationService;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.designation.ExcavationAccessMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serverbound request to apply one work designation to a bounded cuboid. */
public record DesignationAreaPacket(
        DesignationType type,
        BlockPos first,
        BlockPos second,
        ExcavationAccessMode accessMode
) {
    public DesignationAreaPacket {
        type = type == null ? DesignationType.CANCEL : type;
        first = first.immutable();
        second = second.immutable();
        accessMode = accessMode == null ? ExcavationAccessMode.AUTO : accessMode;
    }

    public static void encode(DesignationAreaPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.type.ordinal());
        buffer.writeBlockPos(packet.first);
        buffer.writeBlockPos(packet.second);
        buffer.writeVarInt(packet.accessMode.ordinal());
    }

    public static DesignationAreaPacket decode(FriendlyByteBuf buffer) {
        DesignationType type = DesignationType.byId(buffer.readVarInt());
        BlockPos first = buffer.readBlockPos();
        BlockPos second = buffer.readBlockPos();
        ExcavationAccessMode accessMode = ExcavationAccessMode.byId(buffer.readVarInt());
        return new DesignationAreaPacket(type, first, second, accessMode);
    }

    public static void handle(DesignationAreaPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                DesignationService.Outcome outcome = DesignationService.apply(
                        sender,
                        packet.type,
                        packet.first,
                        packet.second,
                        packet.accessMode
                );
                Component feedback = switch (outcome.status()) {
                    case APPLIED -> Component.translatable(
                            "message.stonebanner.designation.applied",
                            outcome.affected()
                    );
                    case PLANNED -> Component.translatable(
                            "message.stonebanner.designation.excavation_planned",
                            outcome.affected()
                    );
                    case TUNNEL_PLANNED -> Component.translatable(
                            "message.stonebanner.designation.tunnel_planned",
                            outcome.affected()
                    );
                    case NO_TARGETS -> Component.translatable("message.stonebanner.designation.no_targets");
                    case TOO_LARGE -> Component.translatable("message.stonebanner.designation.too_large_server");
                    case INVALID_ACCESS -> Component.translatable("message.stonebanner.designation.invalid_access");
                    case REJECTED -> Component.translatable("message.stonebanner.designation.rejected");
                };
                sender.displayClientMessage(feedback, true);
            });
        }
        context.setPacketHandled(true);
    }
}
