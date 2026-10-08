package dev.stonebanner.network.packet;

import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.citizen.CitizenOrderSequence;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server-validated, one-shot use of ordinary block controls in a FIFO sequence. */
public record CitizenInteractPacket(int npcId, BlockPos target) {
    public CitizenInteractPacket { target = target.immutable(); }
    public static void encode(CitizenInteractPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.npcId);
        buf.writeBlockPos(p.target);
    }
    public static CitizenInteractPacket decode(FriendlyByteBuf buf) {
        return new CitizenInteractPacket(buf.readVarInt(), buf.readBlockPos());
    }
    public static void handle(CitizenInteractPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> accept(sender, p));
        context.setPacketHandled(true);
    }
    private static void accept(ServerPlayer sender, CitizenInteractPacket p) {
        var level = sender.serverLevel();
        if (!sender.isAlive() || sender.isSpectator() || !level.hasChunkAt(p.target)
                || !level.mayInteract(sender, p.target)
                || sender.distanceToSqr(Vec3.atCenterOf(p.target)) > 256D * 256D) return;
        var entity = level.getEntity(p.npcId);
        if (!(entity instanceof HumanNpcEntity npc) || !npc.isAlive()
                || npc.distanceToSqr(sender) > 256D * 256D
                || !npc.citizenData().canBeDirectedBy(sender.getUUID())) return;
        if (!CitizenOrderSequence.interactiveBlock(level.getBlockState(p.target))) {
            sender.displayClientMessage(Component.translatable("message.stonebanner.interaction_unsupported"), true);
            return;
        }
        boolean accepted = npc.orderSequence().enqueue(CitizenOrderQueue.Entry.interact(p.target), sender.getUUID());
        sender.displayClientMessage(Component.translatable(accepted
                ? "message.stonebanner.order_queued" : "message.stonebanner.order_rejected",
                npc.getDisplayName(), npc.orderSequence().pendingCount()), true);
    }
}
