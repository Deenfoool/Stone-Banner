package dev.stonebanner.network.packet;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** Context target order, bounded to the same loaded world and command radius as movement. */
public record CitizenTargetPacket(int npcId,int targetId) {
    public static void encode(CitizenTargetPacket p,FriendlyByteBuf b){b.writeVarInt(p.npcId);b.writeVarInt(p.targetId);}
    public static CitizenTargetPacket decode(FriendlyByteBuf b){return new CitizenTargetPacket(b.readVarInt(),b.readVarInt());}
    public static void handle(CitizenTargetPacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var sender=c.getSender();if(sender!=null)c.enqueueWork(()->{
        if(sender.isSpectator()||!sender.isAlive())return;
        var level=sender.serverLevel();var e=level.getEntity(p.npcId);var target=level.getEntity(p.targetId);
        if(!(e instanceof HumanNpcEntity npc)||!npc.isAlive()||npc.distanceToSqr(sender)>256*256
            ||target==null||!target.isAlive()||target==npc||target.distanceToSqr(sender)>256*256)return;
        if(target instanceof net.minecraft.world.entity.monster.Monster)npc.issueCommand(new ActorCommand.EntityAction(target.getId(),ActorCommand.EntityActionType.ATTACK));
        else npc.issueCommand(new ActorCommand.FollowEntity(target.getId(),2));
    });c.setPacketHandled(true);}
}
