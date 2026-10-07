package dev.stonebanner.network.packet;

import dev.stonebanner.client.network.ClientScreenPacketHandlers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record CitizenInventorySnapshotPacket(int entityId,UUID citizen,ResourceLocation dimension,CompoundTag data){
    public CitizenInventorySnapshotPacket {data=data.copy();}
    @Override public CompoundTag data(){return data.copy();}
    public static void encode(CitizenInventorySnapshotPacket p,FriendlyByteBuf b){b.writeVarInt(p.entityId);b.writeUUID(p.citizen);b.writeResourceLocation(p.dimension);b.writeNbt(p.data);}
    public static CitizenInventorySnapshotPacket decode(FriendlyByteBuf b){int id=b.readVarInt();UUID citizen=b.readUUID();var dimension=b.readResourceLocation();var data=b.readNbt();if(data==null)throw new IllegalArgumentException("Missing citizen snapshot");return new CitizenInventorySnapshotPacket(id,citizen,dimension,data);}
    public static void handle(CitizenInventorySnapshotPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientScreenPacketHandlers.inventory(p)));ctx.setPacketHandled(true);}
}
