package dev.stonebanner.client;

import dev.stonebanner.client.control.OreDiscoveryState;
import dev.stonebanner.client.screen.OreDiscoveriesScreen;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.ControlMode;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.client.screen.TacticalControlScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class ClientRuntime {
    private ClientRuntime() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GameSpeedController.maintain(minecraft);
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        OreDiscoveryState.checkWorld();
        while (ClientKeyMappings.ORE_JOURNAL.consumeClick()) {
            if (!(minecraft.screen instanceof OreDiscoveriesScreen))
                minecraft.setScreen(new OreDiscoveriesScreen(minecraft.screen));
        }
        enforcePerspective(minecraft);
        handleControlModeKey(minecraft);
        synchronizeTacticalScreen(minecraft);
    }

    private static void enforcePerspective(Minecraft minecraft) {
        if (ClientConfig.ENFORCE_THIRD_PERSON.get()
                && minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void handleControlModeKey(Minecraft minecraft) {
        while (ClientKeyMappings.CYCLE_CONTROL_MODE.consumeClick()) {
            cycleControlMode(minecraft);
        }
    }

    public static void cycleControlMode(Minecraft minecraft) {
        ControlMode nextMode = ClientConfig.controlMode().next();
        ClientConfig.setControlMode(nextMode);
        PlayerCommandController.stop();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.stonebanner.control_mode", nextMode.displayName()),
                    true
            );
        }
        synchronizeTacticalScreen(minecraft);
    }

    private static void synchronizeTacticalScreen(Minecraft minecraft) {
        boolean tactical = ClientConfig.controlMode() == ControlMode.TACTICAL;
        if (tactical && minecraft.screen == null) {
            minecraft.setScreen(new TacticalControlScreen());
        } else if (!tactical && minecraft.screen instanceof TacticalControlScreen) {
            minecraft.setScreen(null);
            minecraft.mouseHandler.grabMouse();
        }
    }
}
