package dev.stonebanner.network;

import dev.stonebanner.network.packet.BannerCommunityActionPacket;
import dev.stonebanner.network.packet.BannerCommunitySnapshotPacket;
import dev.stonebanner.settlement.BannerCommunityService.Action;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BannerCommunityPacketTest {
    @Test void allActionsPreserveBannerNameAndSelectedEntity() {
        for (var action : Action.values()) {
            var p=new BannerCommunityActionPacket(new BlockPos(-17,64,33),action,"Каменный лагерь",42);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try { BannerCommunityActionPacket.encode(p,b);assertEquals(p,BannerCommunityActionPacket.decode(b));assertEquals(0,b.readableBytes()); }
            finally { b.release(); }
        }
    }
    @Test void snapshotsPreserveInactiveBannerOwnershipAndUnloadedResidents() {
        for(boolean owner:new boolean[]{false,true}) {
            var p=new BannerCommunitySnapshotPacket(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),
                    new BlockPos(20,64,-10),true,"Камень",owner,true,false,new BlockPos(8,70,8),0,0,1,3,4,16,1);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try { BannerCommunitySnapshotPacket.encode(p,b);assertEquals(p,BannerCommunitySnapshotPacket.decode(b));assertEquals(0,b.readableBytes()); }
            finally { b.release(); }
        }
    }
    @Test void oversizedNamesAreRejectedByWireFormat() {
        var p=new BannerCommunityActionPacket(BlockPos.ZERO,Action.CREATE,"a".repeat(33),-1);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try { assertThrows(io.netty.handler.codec.EncoderException.class,()->BannerCommunityActionPacket.encode(p,b)); }
        finally { b.release(); }
    }
}
