package dev.stonebanner.network;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.network.packet.CitizenInventorySnapshotPacket;
import dev.stonebanner.network.packet.DesignationAreaPacket;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import dev.stonebanner.network.packet.MoveCitizenPacket;
import dev.stonebanner.network.packet.RequestCitizenInventoryPacket;
import dev.stonebanner.network.packet.SetWorkPriorityPacket;
import dev.stonebanner.network.packet.StopCitizenPacket;
import dev.stonebanner.network.packet.StorageSummaryPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

/** Shared packet channel for Stone & Banner gameplay commands and compact/on-demand state snapshots. */
public final class StoneBannerNetwork {
    private static final String PROTOCOL_VERSION = "4";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(StoneAndBanner.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextPacketId;
    private static boolean registered;

    private StoneBannerNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.registerMessage(
                nextPacketId++,
                MoveCitizenPacket.class,
                MoveCitizenPacket::encode,
                MoveCitizenPacket::decode,
                MoveCitizenPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                StopCitizenPacket.class,
                StopCitizenPacket::encode,
                StopCitizenPacket::decode,
                StopCitizenPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                DesignationAreaPacket.class,
                DesignationAreaPacket::encode,
                DesignationAreaPacket::decode,
                DesignationAreaPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SetWorkPriorityPacket.class,
                SetWorkPriorityPacket::encode,
                SetWorkPriorityPacket::decode,
                SetWorkPriorityPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ExcavationPlanSnapshotPacket.class,
                ExcavationPlanSnapshotPacket::encode,
                ExcavationPlanSnapshotPacket::decode,
                ExcavationPlanSnapshotPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                RequestCitizenInventoryPacket.class,
                RequestCitizenInventoryPacket::encode,
                RequestCitizenInventoryPacket::decode,
                RequestCitizenInventoryPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                CitizenInventorySnapshotPacket.class,
                CitizenInventorySnapshotPacket::encode,
                CitizenInventorySnapshotPacket::decode,
                CitizenInventorySnapshotPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                StorageSummaryPacket.class,
                StorageSummaryPacket::encode,
                StorageSummaryPacket::decode,
                StorageSummaryPacket::handle
        );
    }

    public static void sendMoveCitizen(int entityId, BlockPos target) {
        CHANNEL.sendToServer(new MoveCitizenPacket(entityId, target));
    }

    public static void sendStopCitizen(int entityId) {
        CHANNEL.sendToServer(new StopCitizenPacket(entityId));
    }

    public static void sendDesignation(DesignationType type, BlockPos first, BlockPos second) {
        CHANNEL.sendToServer(new DesignationAreaPacket(type, first, second));
    }

    public static void sendWorkPriority(int entityId, int workTypeId, int priorityCode) {
        CHANNEL.sendToServer(new SetWorkPriorityPacket(entityId, workTypeId, priorityCode));
    }

    public static void sendExcavationSnapshot(
            ServerPlayer player,
            List<ExcavationPlanSnapshotPacket.PlanSnapshot> plans
    ) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ExcavationPlanSnapshotPacket(plans)
        );
    }

    public static void requestCitizenInventory(int entityId) {
        CHANNEL.sendToServer(new RequestCitizenInventoryPacket(entityId));
    }

    public static void sendCitizenInventorySnapshot(ServerPlayer player, int entityId, List<ItemStack> stacks) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CitizenInventorySnapshotPacket(entityId, stacks)
        );
    }

    public static void sendStorageSummary(ServerPlayer player, int ladderCount) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new StorageSummaryPacket(ladderCount)
        );
    }
}
