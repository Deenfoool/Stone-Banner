package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CameraSpace;
import dev.stonebanner.control.ControlMode;
import dev.stonebanner.control.MovementPace;
import dev.stonebanner.control.MovementResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Final common movement layer: no position/velocity writes, only vanilla input impulses. */
@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,value=Dist.CLIENT)
public final class CameraRelativeMovement {
    private static final MovementResponse response=new MovementResponse();
    private static LocalPlayer tracked;
    private static float lastLeft,lastForward;
    private static boolean wasRoute;
    @SubscribeEvent public static void onMovementInput(MovementInputUpdateEvent event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || event.getEntity()!=mc.player || !ClientConfig.ENFORCE_THIRD_PERSON.get())return;
        var input=event.getInput();
        if(tracked!=mc.player){response.reset();lastLeft=lastForward=0;tracked=mc.player;wasRoute=false;}
        if(!(mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen)
                || HeroInputController.commandMode() || ConstructionPreviewController.active()
                || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)!=1) {
            response.reset();lastLeft=lastForward=0;mc.player.setSprinting(false);return;
        }
        boolean route=PlayerCommandController.moving();
        if(!route) {
            float left=0,forward=0;
            if(ClientConfig.controlMode()==ControlMode.ACTION) {
                left=(InputBindings.held(mc.options.keyLeft)?1:0)-(InputBindings.held(mc.options.keyRight)?1:0);
                forward=(InputBindings.held(mc.options.keyUp)?1:0)-(InputBindings.held(mc.options.keyDown)?1:0);
            }
            float length=(float)Math.hypot(left,forward);
            if(length>1){left/=length;forward/=length;}
            var movement=CameraSpace.rotateMovement(left,forward,RpgCameraController.cameraYaw()-mc.player.getYRot());
            input.leftImpulse=movement.left();input.forwardImpulse=movement.forward();
        }
        boolean careful=route && PlayerCommandController.movePace()==MovementPace.CAREFUL;
        // Keep automatic ladder descent; do not overwrite its shift/jump intent with false.
        input.shiftKeyDown |= HeroInputController.descend() || careful && !mc.player.isInWater();
        input.jumping |= HeroInputController.jump();
        float length=(float)Math.hypot(input.leftImpulse,input.forwardImpulse);
        float requested=Math.min(1,length);
        if(route)requested*=PlayerCommandController.arrivalScale();
        if(mc.player.isUsingItem())requested*=.2f;
        if(input.shiftKeyDown || careful)requested*=.3f;
        if(PlayerCommandController.navigationFailed() || PlayerCommandController.waitingForPassage() || !route && wasRoute){response.reset();lastLeft=lastForward=0;}
        float speed=response.update(requested);
        if(length>.001f){lastLeft=input.leftImpulse/length;lastForward=input.forwardImpulse/length;}
        input.leftImpulse=lastLeft*speed;input.forwardImpulse=lastForward*speed;
        input.up=input.forwardImpulse>.001f;input.down=input.forwardImpulse<-.001f;
        input.left=input.leftImpulse>.001f;input.right=input.leftImpulse<-.001f;
        boolean run=InputBindings.held(mc.options.keySprint) || route && PlayerCommandController.movePace()==MovementPace.RUN;
        mc.player.setSprinting(run && !input.shiftKeyDown && !careful && !mc.player.isUsingItem() && speed>.1f);
        wasRoute=route;
    }
}
