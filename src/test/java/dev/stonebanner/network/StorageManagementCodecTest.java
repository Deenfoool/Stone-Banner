package dev.stonebanner.network;

import dev.stonebanner.network.packet.StorageManagementActionPacket;
import dev.stonebanner.network.packet.StorageManagementSnapshotPacket;
import dev.stonebanner.storage.StorageManagementService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageManagementCodecTest {
    @Test void actionRoundTripKeepsDimensionTargetAndIntent() {
        var p=new StorageManagementActionPacket(new ResourceLocation("minecraft:overworld"),new BlockPos(12,64,-4),StorageManagementService.Action.REGISTER);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {StorageManagementActionPacket.encode(p,b);assertEquals(p,StorageManagementActionPacket.decode(b));assertEquals(0,b.readableBytes());}
        finally {b.release();}
    }
    @Test void summaryRoundTripKeepsOpeningAndPartialDoubleChestState() {
        var p=new StorageManagementSnapshotPacket(new ResourceLocation("minecraft:overworld"),BlockPos.ZERO,false,1,2,true,4,54,128,StorageManagementService.Result.ADDED);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {StorageManagementSnapshotPacket.encode(p,b);assertEquals(p,StorageManagementSnapshotPacket.decode(b));assertEquals(0,b.readableBytes());}
        finally {b.release();}
    }
    @Test void rejectsImpossibleContainerSummaries() {
        var world=new ResourceLocation("minecraft:overworld");
        assertThrows(IllegalArgumentException.class,()->new StorageManagementSnapshotPacket(world,BlockPos.ZERO,true,3,2,true,0,54,0,StorageManagementService.Result.READY));
        assertThrows(IllegalArgumentException.class,()->new StorageManagementSnapshotPacket(world,BlockPos.ZERO,true,0,0,true,0,0,0,StorageManagementService.Result.READY));
        assertThrows(IllegalArgumentException.class,()->new StorageManagementSnapshotPacket(world,BlockPos.ZERO,true,0,1,true,28,27,0,StorageManagementService.Result.READY));
    }
}
