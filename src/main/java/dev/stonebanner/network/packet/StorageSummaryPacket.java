package dev.stonebanner.network.packet;

import dev.stonebanner.client.control.StorageSummaryState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Compact server-to-client stock summary used by management HUD only. */
public record StorageSummaryPacket(int ladderCount) {
    public StorageSummaryPacket {
        ladderCount = Math.max(0, ladderCount);
    }

    public static void encode(StorageSummaryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.ladderCount());
    }

    public static StorageSummaryPacket decode(FriendlyByteBuf buffer) {
        return new StorageSummaryPacket(buffer.readVarInt());
    }

    public static void handle(StorageSummaryPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> StorageSummaryState.accept(packet.ladderCount())
        ));
        context.setPacketHandled(true);
    }
}
