package dev.stonebanner.network;

import dev.stonebanner.citizen.*;
import dev.stonebanner.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitizenInspectorCodecTest {
    @Test void identityBoundRequestRoundTrips(){var p=new RequestCitizenInventoryPacket(10,UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"));var b=new FriendlyByteBuf(Unpooled.buffer());try{RequestCitizenInventoryPacket.encode(p,b);assertEquals(p,RequestCitizenInventoryPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void fullDataSnapshotRoundTripsAndDoesNotExposeMutableNbt(){var d=new CitizenData();d.practice(CitizenSkill.MINING,12);var tag=d.save();var p=new CitizenInventorySnapshotPacket(10,UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),tag);tag.getCompound("Experience").putInt("mining",0);assertEquals(12,p.data().getCompound("Experience").getInt("mining"));p.data().getCompound("Experience").putInt("mining",0);var b=new FriendlyByteBuf(Unpooled.buffer());try{CitizenInventorySnapshotPacket.encode(p,b);assertEquals(p,CitizenInventorySnapshotPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void nullSnapshotIsRejected(){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeVarInt(10);b.writeUUID(UUID.randomUUID());b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));b.writeNbt(null);assertThrows(IllegalArgumentException.class,()->CitizenInventorySnapshotPacket.decode(b));}finally{b.release();}}
}
