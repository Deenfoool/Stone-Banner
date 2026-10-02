package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents excavation overlays from leaking between worlds or servers. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class ExcavationOverlayEvents {
    private ExcavationOverlayEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ExcavationOverlayState.clear();
    }
}
