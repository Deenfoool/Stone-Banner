package dev.stonebanner.network.packet;
import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** Context work only on visible resource blocks, with the usual worker/job rules. */
public record CitizenWorkTargetPacket(int npcId,BlockPos target) {
    public CitizenWorkTargetPacket {target=target.immutable();}
    public static void encode(CitizenWorkTargetPacket p,FriendlyByteBuf b){b.writeVarInt(p.npcId);b.writeBlockPos(p.target);}
    public static CitizenWorkTargetPacket decode(FriendlyByteBuf b){return new CitizenWorkTargetPacket(b.readVarInt(),b.readBlockPos());}
    public static void handle(CitizenWorkTargetPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var sender=c.getSender();if(sender!=null)c.enqueueWork(()->{
        var level=sender.serverLevel();var e=level.getEntity(p.npcId);
        if(sender.isSpectator()||!sender.isAlive()||!level.hasChunkAt(p.target)||!level.mayInteract(sender,p.target)
            ||sender.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p.target))>256*256
            ||!(e instanceof HumanNpcEntity npc)||!npc.isAlive()||npc.distanceToSqr(sender)>256*256)return;
        var state=level.getBlockState(p.target);
        WorkType type=state.is(net.minecraft.tags.BlockTags.LOGS)?WorkType.FORESTRY:
            state.is(net.minecraftforge.common.Tags.Blocks.ORES)?WorkType.MINING:null;
        if(type==null||!WorkTargetRules.isValid(type,level,p.target))return;
        var board=CitizenJobBoard.forLevel(level);
        long id=board.publish(type,p.target,level.getGameTime());
        board.job(id).ifPresent(job->npc.workController().assign(job));
    });c.setPacketHandled(true);}
}
