package dev.stonebanner.network.packet;

import dev.stonebanner.client.screen.BannerCommunityScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** On-demand banner menu snapshot. Client never determines ownership, inventory or founding eligibility. */
public record BannerCommunitySnapshotPacket(ResourceLocation dimension, BlockPos target, boolean hasCommunity,
        String name, boolean owner, boolean settlement, boolean bannerActive, BlockPos banner,
        int centerX, int centerZ, int residents, int assigned, int beds, int food, int stores) {
    public static void encode(BannerCommunitySnapshotPacket p, FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension); b.writeBlockPos(p.target); b.writeBoolean(p.hasCommunity);
        b.writeUtf(p.name, 32); b.writeBoolean(p.owner); b.writeBoolean(p.settlement); b.writeBoolean(p.bannerActive);
        b.writeBlockPos(p.banner); b.writeInt(p.centerX); b.writeInt(p.centerZ);
        b.writeVarInt(p.residents); b.writeVarInt(p.assigned); b.writeVarInt(p.beds); b.writeVarInt(p.food); b.writeVarInt(p.stores);
    }
    public static BannerCommunitySnapshotPacket decode(FriendlyByteBuf b) {
        return new BannerCommunitySnapshotPacket(b.readResourceLocation(), b.readBlockPos(), b.readBoolean(),
                b.readUtf(32), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBlockPos(), b.readInt(), b.readInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(BannerCommunitySnapshotPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BannerCommunityScreen.open(p)));
        context.setPacketHandled(true);
    }
}
