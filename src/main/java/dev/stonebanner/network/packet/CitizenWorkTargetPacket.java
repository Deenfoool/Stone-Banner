package dev.stonebanner.network.packet;

import dev.stonebanner.citizen.*;
import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Context work is still checked by the server. Queued work claims only existing jobs. */
public record CitizenWorkTargetPacket(int npcId, BlockPos target, boolean append) {
    public CitizenWorkTargetPacket { target = target.immutable(); }
    public CitizenWorkTargetPacket(int npcId, BlockPos target) { this(npcId, target, false); }
    public static void encode(CitizenWorkTargetPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.npcId); b.writeBlockPos(p.target); b.writeBoolean(p.append);
    }
    public static CitizenWorkTargetPacket decode(FriendlyByteBuf b) {
        return new CitizenWorkTargetPacket(b.readVarInt(), b.readBlockPos(), b.readBoolean());
    }
    public static void handle(CitizenWorkTargetPacket p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> {
            var level = sender.serverLevel(); var entity = level.getEntity(p.npcId);
            if (sender.isSpectator() || !sender.isAlive() || !level.hasChunkAt(p.target)
                    || !level.mayInteract(sender, p.target)
                    || sender.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p.target)) > 256 * 256
                    || !(entity instanceof HumanNpcEntity npc) || !npc.isAlive()
                    || !npc.citizenData().canBeDirectedBy(sender.getUUID())
                    || npc.distanceToSqr(sender) > 256 * 256) return;

            var state = level.getBlockState(p.target);
            WorkType type = state.is(net.minecraft.tags.BlockTags.LOGS) ? WorkType.FORESTRY
                    : state.is(net.minecraftforge.common.Tags.Blocks.ORES) ? WorkType.MINING : null;
            if (type == null || !WorkTargetRules.isValid(type, level, p.target)) return;

            if (p.append) {
                boolean ok = npc.orderSequence().enqueue(CitizenOrderQueue.Entry.work(p.target), sender.getUUID());
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        ok ? "message.stonebanner.order_queued" : "message.stonebanner.order_rejected",
                        npc.getDisplayName(), npc.orderSequence().pendingCount()), true);
            } else {
                npc.orderSequence().clear();
                var board = CitizenJobBoard.forLevel(level);
                long id = board.publish(type, p.target, level.getGameTime());
                board.job(id).ifPresent(job -> npc.workController().assign(job));
            }
        });
        ctx.setPacketHandled(true);
    }
}
