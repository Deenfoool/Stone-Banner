package dev.stonebanner.network;

import dev.stonebanner.network.packet.OreDiscoveryActionPacket;
import dev.stonebanner.network.packet.OreDiscoverySnapshotPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OreDiscoveryPacketTest {
    @Test void snapshotPreservesPermissionDismissalAndVisiblePositions() {
        var packet = new OreDiscoverySnapshotPacket(List.of(new OreDiscoverySnapshotPacket.Finding(
                7, ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ore"), true, true, List.of(new BlockPos(2, 30, 4)))));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { OreDiscoverySnapshotPacket.encode(packet, buf); assertEquals(packet, OreDiscoverySnapshotPacket.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void oversizedSnapshotIsRejectedBeforeAllocatingEntries() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { buf.writeVarInt(129); assertThrows(IllegalArgumentException.class, () -> OreDiscoverySnapshotPacket.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void bothActionsRoundTripWithoutClientSuppliedPositions() {
        for (boolean approve : new boolean[]{false, true}) {
            var packet = new OreDiscoveryActionPacket(12, approve);
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try { OreDiscoveryActionPacket.encode(packet, buf); assertEquals(packet, OreDiscoveryActionPacket.decode(buf)); }
            finally { buf.release(); }
        }
    }
}
