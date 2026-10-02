package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CameraSpace;
import dev.stonebanner.control.ControlMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class CameraRelativeMovement {
    private CameraRelativeMovement() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || event.getEntity() != minecraft.player
                || !ClientConfig.ENFORCE_THIRD_PERSON.get()
                || ClientConfig.controlMode() == ControlMode.TACTICAL) {
            return;
        }

        Input input = event.getInput();
        if (input.leftImpulse == 0.0F && input.forwardImpulse == 0.0F) {
            return;
        }

        CameraSpace.MovementVector movement = CameraSpace.rotateMovement(
                input.leftImpulse,
                input.forwardImpulse,
                RpgCameraController.cameraYaw() - minecraft.player.getYRot()
        );
        input.leftImpulse = movement.left();
        input.forwardImpulse = movement.forward();
    }
}
