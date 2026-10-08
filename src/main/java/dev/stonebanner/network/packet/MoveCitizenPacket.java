package dev.stonebanner.network.packet;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serverbound request to move one selected Human NPC to a loaded block position. */
public record MoveCitizenPacket(int entityId, BlockPos target, boolean append) {
    private static final double MAX_COMMAND_DISTANCE_SQR = 256.0D * 256.0D;

    public MoveCitizenPacket {
        target = target.immutable();
    }
    public MoveCitizenPacket(int entityId, BlockPos target) { this(entityId, target, false); }

    public static void encode(MoveCitizenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeBlockPos(packet.target);
        buffer.writeBoolean(packet.append);
    }

    public static MoveCitizenPacket decode(FriendlyByteBuf buffer) {
        return new MoveCitizenPacket(buffer.readVarInt(), buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(MoveCitizenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handleOnServer(sender, packet));
        }
        context.setPacketHandled(true);
    }

    private static void handleOnServer(ServerPlayer sender, MoveCitizenPacket packet) {
        if (!sender.isAlive() || sender.isSpectator() || !sender.serverLevel().hasChunkAt(packet.target)) {
            return;
        }

        Entity entity = sender.serverLevel().getEntity(packet.entityId);
        if (!(entity instanceof HumanNpcEntity npc)
                || !npc.isAlive()
                || !npc.citizenData().canBeDirectedBy(sender.getUUID())
                || npc.distanceToSqr(sender) > MAX_COMMAND_DISTANCE_SQR) {
            return;
        }

        if (packet.append) {
            boolean accepted = npc.orderSequence().enqueue(
                    dev.stonebanner.command.CitizenOrderQueue.Entry.move(packet.target), sender.getUUID());
            sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(accepted
                    ? "message.stonebanner.move_queued" : "message.stonebanner.move_queue_rejected",
                    npc.getDisplayName(), npc.orderSequence().pendingCount()), true);
        } else npc.issueCommand(new ActorCommand.MoveTo(packet.target));
    }
}
