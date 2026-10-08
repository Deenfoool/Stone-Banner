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

        OreDiscoveryState.checkWorld();dev.stonebanner.client.control.ConstructionPreviewController.checkWorld();
        if (!minecraft.player.isAlive()) { PlayerCommandController.stop();dev.stonebanner.client.control.HeroInputController.cancel(); }
        if (minecraft.screen != null) {
            // This screen dispatches inputs directly; other screens own all keyboard input.
            for (var binding : minecraft.options.keyMappings)
                if (binding.getCategory().equals(ClientKeyMappings.CATEGORY)) while(binding.consumeClick()) { }
            enforcePerspective(minecraft);
            return;
        }
        while(ClientKeyMappings.BUILDING.consumeClick())if(minecraft.screen==null)dev.stonebanner.client.screen.ConstructionScreen.requestOpen();
        while(ClientKeyMappings.PRODUCTION.consumeClick())if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("sbproduction menu");
        if (!minecraft.player.isAlive()) PlayerCommandController.stop();
        while (ClientKeyMappings.DEBUG_OVERLAY.consumeClick()) dev.stonebanner.client.hud.DebugOverlay.toggle();
        while (ClientKeyMappings.FOCUS_SELECTED.consumeClick()) dev.stonebanner.client.camera.RpgCameraController.focusSelected();
        while (ClientKeyMappings.RECENTER_CAMERA.consumeClick()) dev.stonebanner.client.camera.RpgCameraController.recenter();
        while (ClientKeyMappings.LAYER_BOUNDARIES.consumeClick()) dev.stonebanner.client.control.MapLayerState.toggle(dev.stonebanner.client.control.MapLayerState.Layer.BOUNDARIES);
        while (ClientKeyMappings.LAYER_RESOURCES.consumeClick()) dev.stonebanner.client.control.MapLayerState.toggle(dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES);
        while (ClientKeyMappings.LAYER_FERTILITY.consumeClick()) dev.stonebanner.client.control.MapLayerState.toggle(dev.stonebanner.client.control.MapLayerState.Layer.FERTILITY);
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
        dev.stonebanner.client.control.HeroInputController.resetGroundClicks();
        ControlMode nextMode = ClientConfig.controlMode().next();
        ClientConfig.setControlMode(nextMode);
        dev.stonebanner.client.control.HeroInputController.cancel();
        PlayerCommandController.cancelPendingActions();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.stonebanner.control_mode", nextMode.displayName()),
                    true
            );
        }
        synchronizeTacticalScreen(minecraft);
    }

    private static void synchronizeTacticalScreen(Minecraft minecraft) {
        boolean tactical = ClientConfig.ENFORCE_THIRD_PERSON.get()||dev.stonebanner.client.control.ConstructionPreviewController.active();
        if (tactical && minecraft.screen == null) {
            minecraft.setScreen(new TacticalControlScreen());
        } else if (!tactical && minecraft.screen instanceof TacticalControlScreen) {
            minecraft.setScreen(null);
            minecraft.mouseHandler.grabMouse();
        }
    }
}
