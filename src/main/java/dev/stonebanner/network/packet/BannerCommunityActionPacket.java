package dev.stonebanner.network.packet;

import dev.stonebanner.settlement.BannerCommunityService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record BannerCommunityActionPacket(BlockPos banner, BannerCommunityService.Action action, String name, int entityId) {
    public BannerCommunityActionPacket { banner = banner.immutable(); }
    public static void encode(BannerCommunityActionPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.banner); b.writeEnum(p.action); b.writeUtf(p.name, 32); b.writeVarInt(p.entityId);
    }
    public static BannerCommunityActionPacket decode(FriendlyByteBuf b) {
        return new BannerCommunityActionPacket(b.readBlockPos(), b.readEnum(BannerCommunityService.Action.class), b.readUtf(32), b.readVarInt());
    }
    public static void handle(BannerCommunityActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> BannerCommunityService.execute(player, p.banner, p.action, p.name, p.entityId));
        context.setPacketHandled(true);
    }
}
