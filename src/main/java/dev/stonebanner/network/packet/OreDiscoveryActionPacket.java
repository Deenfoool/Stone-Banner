package dev.stonebanner.network.packet;

import dev.stonebanner.designation.ExcavationPlanData;
import dev.stonebanner.designation.OreDiscoveryData;
import dev.stonebanner.network.OreDiscoverySyncEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client sends an existing finding ID only. It cannot submit arbitrary ore positions or enlarge a zone. */
public record OreDiscoveryActionPacket(long id, boolean approve) {
    public static void encode(OreDiscoveryActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.id); buf.writeBoolean(packet.approve);
    }
    public static OreDiscoveryActionPacket decode(FriendlyByteBuf buf) {
        return new OreDiscoveryActionPacket(buf.readVarLong(), buf.readBoolean());
    }
    public static void handle(OreDiscoveryActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            var data = OreDiscoveryData.forLevel(player.serverLevel());
            var finding = data.get(packet.id).orElse(null);
            if (finding == null || player.isSpectator()) return;
            if (!packet.approve) {
                data.hide(packet.id, player.getUUID()); OreDiscoverySyncEvents.sync(player); return;
            }
            // Use the same command range as designations, while camera focus never teleports the player.
            if (finding.positions().stream().noneMatch(pos -> player.distanceToSqr(
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 128 * 128)) {
                player.displayClientMessage(Component.translatable("ore.stonebanner.too_far"), true); return;
            }
            data.approve(packet.id);
            boolean inside = finding.positions().stream().anyMatch(pos ->
                    ExcavationPlanData.forLevel(player.serverLevel()).containsActiveTarget(pos));
            player.displayClientMessage(Component.translatable(inside
                    ? "ore.stonebanner.approved" : "ore.stonebanner.expand_zone"), true);
            OreDiscoverySyncEvents.broadcast(player.serverLevel());
        });
        context.setPacketHandled(true);
    }
}
