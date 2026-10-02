package dev.stonebanner.network.packet;

import dev.stonebanner.citizen.CitizenHudCodec;
import dev.stonebanner.citizen.WorkPriority;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative edit of one Citizen work priority cell. */
public record SetWorkPriorityPacket(int entityId, int workTypeId, int priorityCode) {
    private static final double MAX_EDIT_DISTANCE_SQR = 128.0D * 128.0D;

    public static void encode(SetWorkPriorityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeVarInt(packet.workTypeId);
        buffer.writeVarInt(packet.priorityCode);
    }

    public static SetWorkPriorityPacket decode(FriendlyByteBuf buffer) {
        return new SetWorkPriorityPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SetWorkPriorityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handleOnServer(sender, packet));
        }
        context.setPacketHandled(true);
    }

    private static void handleOnServer(ServerPlayer sender, SetWorkPriorityPacket packet) {
        WorkType[] workTypes = WorkType.values();
        if (packet.workTypeId < 0 || packet.workTypeId >= workTypes.length
                || packet.priorityCode < 0 || packet.priorityCode > 4) {
            return;
        }

        Entity entity = sender.serverLevel().getEntity(packet.entityId);
        if (!(entity instanceof HumanNpcEntity npc)
                || !npc.isAlive()
                || npc.distanceToSqr(sender) > MAX_EDIT_DISTANCE_SQR) {
            return;
        }

        WorkPriority priority = CitizenHudCodec.decodePriority(packet.priorityCode);
        npc.setWorkPriority(workTypes[packet.workTypeId], priority);
    }
}
