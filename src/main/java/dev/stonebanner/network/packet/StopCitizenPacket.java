package dev.stonebanner.network.packet;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serverbound request to stop one selected Human NPC. */
public record StopCitizenPacket(int entityId) {
    private static final double MAX_COMMAND_DISTANCE_SQR = 256.0D * 256.0D;

    public static void encode(StopCitizenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
    }

    public static StopCitizenPacket decode(FriendlyByteBuf buffer) {
        return new StopCitizenPacket(buffer.readVarInt());
    }

    public static void handle(StopCitizenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handleOnServer(sender, packet));
        }
        context.setPacketHandled(true);
    }

    private static void handleOnServer(ServerPlayer sender, StopCitizenPacket packet) {
        Entity entity = sender.serverLevel().getEntity(packet.entityId);
        if (!(entity instanceof HumanNpcEntity npc)
                || !npc.isAlive()
                || npc.distanceToSqr(sender) > MAX_COMMAND_DISTANCE_SQR) {
            return;
        }

        npc.issueCommand(new ActorCommand.Stop());
    }
}
