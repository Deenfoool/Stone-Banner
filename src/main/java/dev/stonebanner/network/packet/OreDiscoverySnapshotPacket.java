package dev.stonebanner.network.packet;

import dev.stonebanner.client.control.OreDiscoveryState;
import dev.stonebanner.designation.OreDiscoveryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record OreDiscoverySnapshotPacket(List<Finding> findings) {
    public OreDiscoverySnapshotPacket {
        findings = List.copyOf(findings);
        if (findings.size() > OreDiscoveryData.MAX_FINDINGS) throw new IllegalArgumentException("Too many findings");
    }
    public static void encode(OreDiscoverySnapshotPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.findings.size());
        for (Finding f : packet.findings) {
            buf.writeVarLong(f.id()); buf.writeResourceLocation(f.block());
            buf.writeBoolean(f.approved()); buf.writeBoolean(f.hidden());
            buf.writeVarInt(f.positions().size());
            f.positions().forEach(buf::writeBlockPos);
        }
    }
    public static OreDiscoverySnapshotPacket decode(FriendlyByteBuf buf) {
        int count = bounded(buf.readVarInt(), OreDiscoveryData.MAX_FINDINGS);
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = buf.readVarLong(); ResourceLocation block = buf.readResourceLocation();
            boolean approved = buf.readBoolean(), hidden = buf.readBoolean();
            int size = bounded(buf.readVarInt(), OreDiscoveryData.MAX_BLOCKS);
            List<BlockPos> positions = new ArrayList<>();
            for (int j = 0; j < size; j++) positions.add(buf.readBlockPos());
            findings.add(new Finding(id, block, approved, hidden, positions));
        }
        return new OreDiscoverySnapshotPacket(findings);
    }
    private static int bounded(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid finding count");
        return count;
    }
    public static void handle(OreDiscoverySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> OreDiscoveryState.accept(packet.findings)));
        context.setPacketHandled(true);
    }
    public record Finding(long id, ResourceLocation block, boolean approved, boolean hidden, List<BlockPos> positions) {
        public Finding {
            positions = positions.stream().map(BlockPos::immutable).toList();
            if (id <= 0 || positions.isEmpty() || positions.size() > OreDiscoveryData.MAX_BLOCKS)
                throw new IllegalArgumentException("Invalid finding");
        }
        public BlockPos anchor() { return positions.get(0); }
    }
}
