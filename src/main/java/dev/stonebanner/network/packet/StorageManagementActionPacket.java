package dev.stonebanner.network.packet;

import dev.stonebanner.storage.StorageManagementService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record StorageManagementActionPacket(ResourceLocation dimension, BlockPos target, StorageManagementService.Action action) {
    public StorageManagementActionPacket { target=target.immutable(); }
    public static void encode(StorageManagementActionPacket p,FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension);b.writeBlockPos(p.target);b.writeEnum(p.action);
    }
    public static StorageManagementActionPacket decode(FriendlyByteBuf b) {
        return new StorageManagementActionPacket(b.readResourceLocation(),b.readBlockPos(),b.readEnum(StorageManagementService.Action.class));
    }
    public static void handle(StorageManagementActionPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();var player=ctx.getSender();
        if(player!=null)ctx.enqueueWork(()-> {
            if(player.serverLevel().dimension().location().equals(p.dimension))StorageManagementService.execute(player,p.target,p.action);
        });ctx.setPacketHandled(true);
    }
}
