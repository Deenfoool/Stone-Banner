package dev.stonebanner.network;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.designation.OreDiscoveryData;
import dev.stonebanner.network.packet.OreDiscoverySnapshotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class OreDiscoverySyncEvents {
    private OreDiscoverySyncEvents() {}
    public static void sync(ServerPlayer player) {
        var snapshots = OreDiscoveryData.forLevel(player.serverLevel()).findings().stream()
                .map(f -> new OreDiscoverySnapshotPacket.Finding(f.id(), ResourceLocation.tryParse(f.blockId()),
                        f.approved(), f.hiddenFor(player.getUUID()), f.positions())).toList();
        StoneBannerNetwork.sendOreFindings(player, snapshots);
    }
    public static void broadcast(ServerLevel level) { level.players().forEach(OreDiscoverySyncEvents::sync); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { syncEvent(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { syncEvent(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { syncEvent(event); }
    private static void syncEvent(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
