package dev.stonebanner.network;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.network.packet.TacticalActionPacket;
import dev.stonebanner.network.packet.OpenTacticalNpcPacket;
import dev.stonebanner.network.packet.GeologyActionPacket;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import dev.stonebanner.geology.GeologyService;
import dev.stonebanner.network.packet.BannerCommunityActionPacket;
import dev.stonebanner.network.packet.BannerCommunitySnapshotPacket;
import dev.stonebanner.settlement.BannerCommunityService;
import dev.stonebanner.network.packet.OreDiscoverySnapshotPacket;
import dev.stonebanner.network.packet.OreDiscoveryActionPacket;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.designation.ExcavationAccessMode;
import dev.stonebanner.network.packet.CitizenInventorySnapshotPacket;
import dev.stonebanner.network.packet.DesignationAreaPacket;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import dev.stonebanner.network.packet.ExtendExcavationPacket;
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
    private static final String PROTOCOL_VERSION = "12";

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
        CHANNEL.registerMessage(nextPacketId++,dev.stonebanner.network.packet.CitizenWorkTargetPacket.class,
            dev.stonebanner.network.packet.CitizenWorkTargetPacket::encode,dev.stonebanner.network.packet.CitizenWorkTargetPacket::decode,
            dev.stonebanner.network.packet.CitizenWorkTargetPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++,dev.stonebanner.network.packet.CitizenTargetPacket.class,
            dev.stonebanner.network.packet.CitizenTargetPacket::encode,dev.stonebanner.network.packet.CitizenTargetPacket::decode,
            dev.stonebanner.network.packet.CitizenTargetPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, TacticalActionPacket.class, TacticalActionPacket::encode,
                TacticalActionPacket::decode, TacticalActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, OpenTacticalNpcPacket.class, OpenTacticalNpcPacket::encode,
                OpenTacticalNpcPacket::decode, OpenTacticalNpcPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, GeologyActionPacket.class,
                GeologyActionPacket::encode, GeologyActionPacket::decode, GeologyActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, GeologySnapshotPacket.class,
                GeologySnapshotPacket::encode, GeologySnapshotPacket::decode, GeologySnapshotPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(nextPacketId++, BannerCommunityActionPacket.class,
                BannerCommunityActionPacket::encode, BannerCommunityActionPacket::decode, BannerCommunityActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, BannerCommunitySnapshotPacket.class,
                BannerCommunitySnapshotPacket::encode, BannerCommunitySnapshotPacket::decode, BannerCommunitySnapshotPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, OreDiscoverySnapshotPacket.class,
                OreDiscoverySnapshotPacket::encode, OreDiscoverySnapshotPacket::decode,
                OreDiscoverySnapshotPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, OreDiscoveryActionPacket.class,
                OreDiscoveryActionPacket::encode, OreDiscoveryActionPacket::decode,
                OreDiscoveryActionPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
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
                ExtendExcavationPacket.class,
                ExtendExcavationPacket::encode,
                ExtendExcavationPacket::decode,
                ExtendExcavationPacket::handle
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

    public static void sendTacticalAction(TacticalActionPacket packet) { CHANNEL.sendToServer(packet); }
    public static void openTacticalNpc(ServerPlayer player, int entityId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTacticalNpcPacket(player.serverLevel().dimension().location(), entityId));
    }
    public static void requestMapLayers(BlockPos center) {
        CHANNEL.sendToServer(new GeologyActionPacket(true, center, GeologyService.Action.OPEN, -1));
    }
    public static void sendGeologyAction(BlockPos target, GeologyService.Action action, int npcId) {
        CHANNEL.sendToServer(new GeologyActionPacket(false, target, action, npcId));
    }
    public static void sendGeology(ServerPlayer player, GeologySnapshotPacket snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    public static void sendBannerAction(BlockPos pos, BannerCommunityService.Action action, String name, int entityId) {
        CHANNEL.sendToServer(new BannerCommunityActionPacket(pos, action, name, entityId));
    }
    public static void sendBannerCommunity(ServerPlayer player, BannerCommunitySnapshotPacket snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }

    public static void sendOreAction(long id, boolean approve) {
        CHANNEL.sendToServer(new OreDiscoveryActionPacket(id, approve));
    }

    public static void sendOreFindings(ServerPlayer player, List<OreDiscoverySnapshotPacket.Finding> findings) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OreDiscoverySnapshotPacket(findings));
    }

    public static void sendCitizenWorkTarget(int npcId,BlockPos target) {
        CHANNEL.sendToServer(new dev.stonebanner.network.packet.CitizenWorkTargetPacket(npcId,target));
    }
    public static void sendCitizenTarget(int npcId,int targetId) {
        CHANNEL.sendToServer(new dev.stonebanner.network.packet.CitizenTargetPacket(npcId,targetId));
    }
    public static void sendMoveCitizen(int entityId, BlockPos target) {
        CHANNEL.sendToServer(new MoveCitizenPacket(entityId, target));
    }

    public static void sendStopCitizen(int entityId) {
        CHANNEL.sendToServer(new StopCitizenPacket(entityId));
    }

    public static void sendDesignation(DesignationType type, BlockPos first, BlockPos second) {
        sendDesignation(type, first, second, ExcavationAccessMode.AUTO);
    }

    public static void sendDesignation(DesignationType type, BlockPos first, BlockPos second,
                                       ExcavationAccessMode accessMode) {
        CHANNEL.sendToServer(new DesignationAreaPacket(type, first, second, accessMode));
    }

    public static void sendExtendExcavation(long planId, int additionalLength) {
        CHANNEL.sendToServer(new ExtendExcavationPacket(planId, additionalLength));
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
