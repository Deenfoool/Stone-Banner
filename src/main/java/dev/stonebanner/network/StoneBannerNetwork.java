package dev.stonebanner.network;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.network.packet.DesignationAreaPacket;
import dev.stonebanner.network.packet.MoveCitizenPacket;
import dev.stonebanner.network.packet.SetWorkPriorityPacket;
import dev.stonebanner.network.packet.StopCitizenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Shared packet channel for client-to-server Stone & Banner commands. */
public final class StoneBannerNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StoneAndBanner.MOD_ID, "main"),
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
}
