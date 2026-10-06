package dev.stonebanner.storage;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Low-frequency compact stock sync for management HUD. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class StorageSyncEvents {
    private static final int SYNC_INTERVAL_TICKS = 20;

    private StorageSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        int ladders = StorageData.forLevel(player.serverLevel()).countItem(player.serverLevel(), Items.LADDER);
        StoneBannerNetwork.sendStorageSummary(player, ladders);
    }
}
