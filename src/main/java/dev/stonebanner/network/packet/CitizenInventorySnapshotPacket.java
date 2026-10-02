package dev.stonebanner.network.packet;

import dev.stonebanner.citizen.CitizenInventory;
import dev.stonebanner.client.control.CitizenInventoryClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One-shot server-to-client snapshot for the Citizen inventory inspector tab. */
public record CitizenInventorySnapshotPacket(int entityId, List<ItemStack> stacks) {
    public CitizenInventorySnapshotPacket {
        stacks = stacks == null ? List.of() : stacks.stream().map(ItemStack::copy).toList();
    }

    public static void encode(CitizenInventorySnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        int size = Math.min(CitizenInventory.SLOT_COUNT, packet.stacks.size());
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            buffer.writeItem(packet.stacks.get(index));
        }
    }

    public static CitizenInventorySnapshotPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int size = Math.min(CitizenInventory.SLOT_COUNT, Math.max(0, buffer.readVarInt()));
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            stacks.add(buffer.readItem());
        }
        return new CitizenInventorySnapshotPacket(entityId, stacks);
    }

    public static void handle(CitizenInventorySnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CitizenInventoryClientCache.update(packet.entityId, packet.stacks));
        context.setPacketHandled(true);
    }
}
