package dev.stonebanner.network.packet;

import dev.stonebanner.client.control.ExcavationOverlayState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Small server-to-client snapshot used only to render persistent excavation overlays. */
public record ExcavationPlanSnapshotPacket(List<PlanSnapshot> plans) {
    private static final int MAX_PLANS = 256;

    public ExcavationPlanSnapshotPacket {
        plans = plans == null ? List.of() : List.copyOf(plans);
        if (plans.size() > MAX_PLANS) {
            throw new IllegalArgumentException("Too many excavation plans in one snapshot");
        }
    }

    public static void encode(ExcavationPlanSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.plans.size());
        for (PlanSnapshot plan : packet.plans) {
            buffer.writeVarLong(plan.id());
            buffer.writeBlockPos(plan.min());
            buffer.writeBlockPos(plan.max());
            buffer.writeByte(plan.modeCode());
            buffer.writeInt(plan.currentSlice());
            buffer.writeByte(plan.step());
            buffer.writeBoolean(plan.hazardPaused());
        }
    }

    public static ExcavationPlanSnapshotPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PLANS) {
            throw new IllegalArgumentException("Invalid excavation plan snapshot size: " + count);
        }
        ArrayList<PlanSnapshot> plans = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            plans.add(new PlanSnapshot(
                    buffer.readVarLong(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readByte(),
                    buffer.readInt(),
                    buffer.readByte(),
                    buffer.readBoolean()
            ));
        }
        return new ExcavationPlanSnapshotPacket(plans);
    }

    public static void handle(ExcavationPlanSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ExcavationOverlayState.accept(packet.plans)
        ));
        context.setPacketHandled(true);
    }

    public record PlanSnapshot(
            long id,
            BlockPos min,
            BlockPos max,
            int modeCode,
            int currentSlice,
            int step,
            boolean hazardPaused
    ) {
        public PlanSnapshot {
            min = min.immutable();
            max = max.immutable();
            step = step < 0 ? -1 : 1;
        }
    }
}
