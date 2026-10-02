package dev.stonebanner.network;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.designation.ExcavationPlanData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the small client excavation overlay mirror correct across joins and dimension changes. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class ExcavationPlanSyncEvents {
    private ExcavationPlanSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event);
    }

    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExcavationPlanData.forLevel(player.serverLevel()).syncTo(player);
        }
    }
}
