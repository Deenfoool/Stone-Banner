package dev.stonebanner.network.packet;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Context target command. Append uses stable UUID and bounded server-owned FIFO. */
public record CitizenTargetPacket(int npcId, int targetId, boolean append) {
    public CitizenTargetPacket(int npcId, int targetId) { this(npcId, targetId, false); }
    public static void encode(CitizenTargetPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.npcId); b.writeVarInt(p.targetId); b.writeBoolean(p.append);
    }
    public static CitizenTargetPacket decode(FriendlyByteBuf b) {
        return new CitizenTargetPacket(b.readVarInt(), b.readVarInt(), b.readBoolean());
    }
    public static void handle(CitizenTargetPacket p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> {
            if (sender.isSpectator() || !sender.isAlive()) return;
            var level = sender.serverLevel();
            var entity = level.getEntity(p.npcId);
            var target = level.getEntity(p.targetId);
            if (!(entity instanceof HumanNpcEntity npc) || !npc.isAlive()
                    || !npc.citizenData().canBeDirectedBy(sender.getUUID())
                    || npc.distanceToSqr(sender) > 256 * 256
                    || target == null || !target.isAlive() || target == npc
                    || target.distanceToSqr(sender) > 256 * 256) return;
            boolean hostile = target instanceof net.minecraft.world.entity.monster.Monster;
            if (p.append) {
                boolean ok = npc.orderSequence().enqueue(CitizenOrderQueue.Entry.target(
                        hostile ? CitizenOrderQueue.Kind.ATTACK : CitizenOrderQueue.Kind.FOLLOW,
                        target.getUUID()));
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        ok ? "message.stonebanner.order_queued" : "message.stonebanner.order_rejected",
                        npc.getDisplayName(), npc.orderSequence().pendingCount()), true);
            } else if (hostile) {
                npc.issueCommand(new ActorCommand.EntityAction(target.getId(), ActorCommand.EntityActionType.ATTACK));
            } else npc.issueCommand(new ActorCommand.FollowEntity(target.getId(), 2));
        });
        ctx.setPacketHandled(true);
    }
}
