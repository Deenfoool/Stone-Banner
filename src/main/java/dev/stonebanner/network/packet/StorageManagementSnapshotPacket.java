package dev.stonebanner.network.packet;

import dev.stonebanner.storage.StorageManagementService.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Compact container summary. No inventory or client-generated eligibility is accepted by the server. */
public record StorageManagementSnapshotPacket(ResourceLocation dimension,BlockPos target,boolean opening,
        int registered,int members,boolean manageable,int occupied,int slots,int items,Result result) {
    public StorageManagementSnapshotPacket {
        target=target.immutable();
        if(members<1||members>2||registered<0||registered>members||occupied<0||slots<occupied||slots>4096||items<0)
            throw new IllegalArgumentException("Invalid storage summary");
    }
    public static void encode(StorageManagementSnapshotPacket p,FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension);b.writeBlockPos(p.target);b.writeBoolean(p.opening);
        b.writeVarInt(p.registered);b.writeVarInt(p.members);b.writeBoolean(p.manageable);
        b.writeVarInt(p.occupied);b.writeVarInt(p.slots);b.writeVarInt(p.items);b.writeEnum(p.result);
    }
    public static StorageManagementSnapshotPacket decode(FriendlyByteBuf b) {
        return new StorageManagementSnapshotPacket(b.readResourceLocation(),b.readBlockPos(),b.readBoolean(),b.readVarInt(),
                b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readEnum(Result.class));
    }
    public static void handle(StorageManagementSnapshotPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->dev.stonebanner.client.network.ClientScreenPacketHandlers.storage(p)));ctx.setPacketHandled(true);
    }
}
