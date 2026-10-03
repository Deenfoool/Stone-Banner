package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Controls the local integrated-server clock from the tactical HUD. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class GameSpeedController {
    private static volatile Speed speed = Speed.NORMAL;

    private GameSpeedController() {
    }

    public static Speed speed() {
        return speed;
    }

    public static void setSpeed(Speed requested) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            speed = Speed.NORMAL;
            return;
        }
        speed = requested == null ? Speed.NORMAL : requested;
        server.nextTickTime = speed == Speed.PAUSED ? Util.getMillis() + 250L : Util.getMillis();
    }

    public static void maintain(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            speed = Speed.NORMAL;
        } else if (speed == Speed.PAUSED) {
            server.nextTickTime = Util.getMillis() + 250L;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.getServer() instanceof IntegratedServer server)) {
            return;
        }
        if (speed == Speed.PAUSED) {
            server.nextTickTime = Util.getMillis() + 250L;
        } else if (speed.multiplier() > 1) {
            server.nextTickTime -= 50L - Math.round(50.0D / speed.multiplier());
        }
    }

    public enum Speed {
        PAUSED(0),
        NORMAL(1),
        DOUBLE(2),
        TRIPLE(3);

        private final int multiplier;

        Speed(int multiplier) {
            this.multiplier = multiplier;
        }

        public int multiplier() {
            return multiplier;
        }
    }
}
