package dev.stonebanner.client.network;

import dev.stonebanner.client.control.MapLayerState;
import dev.stonebanner.client.screen.BannerCommunityScreen;
import dev.stonebanner.client.screen.CitizenDetailsScreen;
import dev.stonebanner.client.screen.GeologyResearchScreen;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.packet.BannerCommunitySnapshotPacket;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import dev.stonebanner.network.packet.OpenTacticalNpcPacket;
import net.minecraft.client.Minecraft;

/** Called only inside DistExecutor's client branch; common packet bytecode never constructs screens. */
public final class ClientScreenPacketHandlers {
    private ClientScreenPacketHandlers() {}
    public static void production(dev.stonebanner.network.packet.ProductionSnapshotPacket packet){dev.stonebanner.client.screen.ProductionScreen.open(packet);}

    public static void openCitizen(OpenTacticalNpcPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().location().equals(packet.dimension())
                && mc.level.getEntity(packet.entityId()) instanceof HumanNpcEntity)
            mc.setScreen(new CitizenDetailsScreen(mc.screen, packet.entityId(), CitizenDetailsScreen.Tab.OVERVIEW));
    }

    public static void geology(GeologySnapshotPacket packet) {
        if (packet.research()) GeologyResearchScreen.open(packet);
        else MapLayerState.accept(packet);
    }

    public static void community(BannerCommunitySnapshotPacket packet) {
        BannerCommunityScreen.open(packet);
    }
    public static void journal(dev.stonebanner.network.packet.VillageJournalPacket packet) {
        dev.stonebanner.client.screen.VillageJournalScreen.open(packet);
    }
    public static void recruitment(dev.stonebanner.network.packet.VillageRecruitmentPacket packet) {
        dev.stonebanner.client.screen.VillageRecruitmentScreen.open(packet);
    }
    public static void inventory(dev.stonebanner.network.packet.CitizenInventorySnapshotPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension()))return;
        if(mc.level.getEntity(packet.entityId()) instanceof HumanNpcEntity npc&&npc.getUUID().equals(packet.citizen()))
            dev.stonebanner.client.control.CitizenInventoryClientCache.update(packet.dimension(),packet.citizen(),packet.data());
    }
}
