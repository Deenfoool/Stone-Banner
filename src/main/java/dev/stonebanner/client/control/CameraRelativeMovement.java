package dev.stonebanner.client.control;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CameraSpace;
import dev.stonebanner.control.ControlMode;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class CameraRelativeMovement {
    @SubscribeEvent public static void onMovementInput(MovementInputUpdateEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player || !ClientConfig.ENFORCE_THIRD_PERSON.get()) return;
        var input = event.getInput();
        if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)!=1) {
            input.leftImpulse=0;input.forwardImpulse=0;input.jumping=false;input.shiftKeyDown=false;PlayerCommandController.stop();return;
        }
        if (!(mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen) || HeroInputController.commandMode()) return;
        if (!PlayerCommandController.moving()) {
            float left = 0, forward = 0;
            if (ClientConfig.controlMode() == ControlMode.ACTION) {
                left = (HeroInputController.down(org.lwjgl.glfw.GLFW.GLFW_KEY_A)?1:0)-(HeroInputController.down(org.lwjgl.glfw.GLFW.GLFW_KEY_D)?1:0);
                forward = (HeroInputController.down(org.lwjgl.glfw.GLFW.GLFW_KEY_W)?1:0)-(HeroInputController.down(org.lwjgl.glfw.GLFW.GLFW_KEY_S)?1:0);
            }
            var movement = CameraSpace.rotateMovement(left, forward, RpgCameraController.cameraYaw()-mc.player.getYRot());
            input.leftImpulse = movement.left(); input.forwardImpulse = movement.forward();
        }
        input.up=input.forwardImpulse>0;input.down=input.forwardImpulse<0;input.left=input.leftImpulse>0;input.right=input.leftImpulse<0;
        if(mc.player.isUsingItem()){input.forwardImpulse*=.2f;input.leftImpulse*=.2f;}
        if(HeroInputController.descend()){input.forwardImpulse*=.3f;input.leftImpulse*=.3f;}
        input.jumping |= HeroInputController.jump();
        input.shiftKeyDown = HeroInputController.descend();
        mc.player.setSprinting(HeroInputController.down(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) && !input.shiftKeyDown && !mc.player.isUsingItem()
                && (input.forwardImpulse != 0 || input.leftImpulse != 0));
    }
}
