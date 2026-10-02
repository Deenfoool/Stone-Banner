package dev.stonebanner.network.packet;

import dev.stonebanner.designation.DesignationService;
import dev.stonebanner.designation.DesignationType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serverbound request to apply one work designation to a bounded cuboid. */
public record DesignationAreaPacket(DesignationType type, BlockPos first, BlockPos second) {
    public DesignationAreaPacket {
        type = type == null ? DesignationType.CANCEL : type;
        first = first.immutable();
        second = second.immutable();
    }

    public static void encode(DesignationAreaPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.type.ordinal());
        buffer.writeBlockPos(packet.first);
        buffer.writeBlockPos(packet.second);
    }

    public static DesignationAreaPacket decode(FriendlyByteBuf buffer) {
        DesignationType type = DesignationType.byId(buffer.readVarInt());
        return new DesignationAreaPacket(type, buffer.readBlockPos(), buffer.readBlockPos());
    }

    public static void handle(DesignationAreaPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> DesignationService.apply(sender, packet.type, packet.first, packet.second));
        }
        context.setPacketHandled(true);
    }
}
