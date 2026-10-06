package dev.stonebanner.network.packet;

import dev.stonebanner.client.screen.CitizenDetailsScreen;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenTacticalNpcPacket(ResourceLocation dimension,int entityId) {
    public static void encode(OpenTacticalNpcPacket p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeVarInt(p.entityId);}
    public static OpenTacticalNpcPacket decode(FriendlyByteBuf b){return new OpenTacticalNpcPacket(b.readResourceLocation(),b.readVarInt());}
    public static void handle(OpenTacticalNpcPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().location().equals(p.dimension)&&mc.level.getEntity(p.entityId) instanceof HumanNpcEntity)mc.setScreen(new CitizenDetailsScreen(mc.screen,p.entityId,CitizenDetailsScreen.Tab.OVERVIEW));}));ctx.setPacketHandled(true);}
}
