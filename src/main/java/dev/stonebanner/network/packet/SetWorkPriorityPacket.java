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
public record SetWorkPriorityPacket(int entityId, java.util.UUID citizen,net.minecraft.resources.ResourceLocation dimension,int workTypeId, int priorityCode) {
    private static final double MAX_EDIT_DISTANCE_SQR = 128.0D * 128.0D;

    public static void encode(SetWorkPriorityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeUUID(packet.citizen);buffer.writeResourceLocation(packet.dimension);
        buffer.writeVarInt(packet.workTypeId);
        buffer.writeVarInt(packet.priorityCode);
    }

    public static SetWorkPriorityPacket decode(FriendlyByteBuf buffer) {
        return new SetWorkPriorityPacket(buffer.readVarInt(),buffer.readUUID(),buffer.readResourceLocation(),buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SetWorkPriorityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> apply(sender, packet));
        }
        context.setPacketHandled(true);
    }

    public static boolean apply(ServerPlayer sender, SetWorkPriorityPacket packet) {
        WorkType[] workTypes = WorkType.values();
        if (packet.workTypeId < 0 || packet.workTypeId >= workTypes.length
                || packet.priorityCode < 0 || packet.priorityCode > 4) {
            return false;
        }

        Entity entity = sender.serverLevel().getEntity(packet.entityId);
        if (!(entity instanceof HumanNpcEntity npc)
                || !sender.isAlive() || sender.isSpectator() || !sender.serverLevel().dimension().location().equals(packet.dimension)
                || !npc.getUUID().equals(packet.citizen)
                || !npc.isAlive()
                || !npc.citizenData().canBeDirectedBy(sender.getUUID())
                || npc.distanceToSqr(sender) > MAX_EDIT_DISTANCE_SQR) {
            return false;
        }

        WorkPriority priority = CitizenHudCodec.decodePriority(packet.priorityCode);
        npc.setWorkPriority(workTypes[packet.workTypeId], priority);
        return true;
    }
}
