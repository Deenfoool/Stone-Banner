package dev.stonebanner.network;
import dev.stonebanner.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TacticalActionPacketTest {
    @Test void allActionsPreserveTargetAndClickedBlockFace(){for(var a:TacticalActionPacket.Action.values()){var p=new TacticalActionPacket(a,42,new BlockPos(-3,64,8),Direction.WEST,new Vec3(-3,64.5,8.25));var b=new FriendlyByteBuf(Unpooled.buffer());try{TacticalActionPacket.encode(p,b);assertEquals(p,TacticalActionPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}}
    @Test void npcMenuReplyCarriesDimension(){var p=new OpenTacticalNpcPacket(ResourceLocation.fromNamespaceAndPath("minecraft","the_nether"),12);var b=new FriendlyByteBuf(Unpooled.buffer());try{OpenTacticalNpcPacket.encode(p,b);assertEquals(p,OpenTacticalNpcPacket.decode(b));}finally{b.release();}}
}
