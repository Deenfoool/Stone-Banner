package dev.stonebanner.network.packet;

import dev.stonebanner.client.network.ClientScreenPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenTacticalNpcPacket(ResourceLocation dimension,int entityId) {
    public static void encode(OpenTacticalNpcPacket p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeVarInt(p.entityId);}
    public static OpenTacticalNpcPacket decode(FriendlyByteBuf b){return new OpenTacticalNpcPacket(b.readResourceLocation(),b.readVarInt());}
    public static void handle(OpenTacticalNpcPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientScreenPacketHandlers.openCitizen(p)));ctx.setPacketHandled(true);}
}
