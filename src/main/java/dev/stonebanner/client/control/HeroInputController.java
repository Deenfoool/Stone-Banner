package dev.stonebanner.client.control;

import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.ControlMode;
import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.client.camera.RpgCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;

/** Held hero actions use the real client game mode, inventory and server vanilla packets. */
public final class HeroInputController {
    private static boolean commands, actionHeld, moveHeld;
    private static net.minecraft.client.player.LocalPlayer tracked;
    private static int selectedSlot = -1, approachCooldown;
    private static HitResult target;
    public static boolean commandMode() { return commands; }
    public static boolean down(int key) { return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),key)==GLFW.GLFW_PRESS; }
    public static boolean jump() { return down(GLFW.GLFW_KEY_SPACE); }
    public static boolean descend() { return down(GLFW.GLFW_KEY_LEFT_CONTROL)||down(GLFW.GLFW_KEY_RIGHT_CONTROL); }
    public static boolean manualMovement() { return down(GLFW.GLFW_KEY_W)||down(GLFW.GLFW_KEY_A)||down(GLFW.GLFW_KEY_S)||down(GLFW.GLFW_KEY_D); }
    public static void toggleCommands() {
        cancel(); PlayerCommandController.stop(); commands=!commands;
        CitizenSelectionController.clear(); DesignationController.deactivate(); TunnelExtensionController.deactivate();
        RpgCameraController.recenter();
    }
    public static void setMoveHeld(boolean held) { moveHeld=held; }
    public static void setActionHeld(boolean held) {
        if(!held) { cancelAction(); return; }
        PlayerCommandController.stop(); actionHeld=true;
    }
    public static void cancel() { moveHeld=false; cancelAction(); }
    private static void cancelAction() {
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.gameMode!=null) {
            if(mc.player.isUsingItem()) { if(target!=null)aim(target instanceof EntityHitResult e?e.getEntity().getBoundingBox().getCenter():target.getLocation()); sendAim(); mc.gameMode.releaseUsingItem(mc.player); }
            mc.gameMode.stopDestroyBlock();
        }
        if(actionHeld) PlayerCommandController.stop();
        actionHeld=false; target=null; approachCooldown=0;
    }
    public static void tick(HitResult hover, boolean overUi, HitResult movementHit) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.gameMode==null) { cancel(); commands=false; tracked=null; return; }
        if(tracked!=mc.player) { cancel(); commands=false; tracked=mc.player; selectedSlot=mc.player.getInventory().selected; }
        if(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)!=1) { cancel(); return; }
        if(commands||mc.player.isSpectator()||!mc.player.isAlive()) { cancel(); return; }
        if(actionHeld && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_RIGHT)!=GLFW.GLFW_PRESS)cancelAction();
        if(moveHeld && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_LEFT)!=GLFW.GLFW_PRESS)moveHeld=false;
        if(approachCooldown>0)approachCooldown--;
        if(selectedSlot!=mc.player.getInventory().selected) { cancelAction(); selectedSlot=mc.player.getInventory().selected; }
        if(moveHeld&&!overUi&&ClientConfig.controlMode()==ControlMode.HYBRID&&mc.player.tickCount%5==0
                && movementHit instanceof BlockHitResult block) PlayerCommandController.moveTo(block);
        if(!actionHeld||overUi||RpgCameraController.viewObstructed()) { if(overUi&&actionHeld)cancelAction(); return; }
        target=hover;
        var item=mc.player.getMainHandItem();
        boolean using=item.getUseDuration()>0;
        if(hover!=null) aim(hover instanceof EntityHitResult e?e.getEntity().getBoundingBox().getCenter():hover.getLocation());
        if(using) {
            mc.gameMode.stopDestroyBlock();
            if(!mc.player.isUsingItem()) { sendAim(); syncSlot(); mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND); }
            return;
        }
        if(hover instanceof EntityHitResult entityHit) {
            mc.gameMode.stopDestroyBlock(); var entity=entityHit.getEntity();
            if(!entity.isAttackable()||entity instanceof net.minecraft.world.entity.item.ItemEntity) return;
            if(!mc.player.canReach(entity,-.3)||!TacticalInteractionRules.visible(mc.level,mc.player,entity.getBoundingBox().getCenter(),null)) {
                approach(entity.getBoundingBox().getCenter(),null,mc.player.getEntityReach()); return;
            }
            PlayerCommandController.stop();
            if(mc.player.getAttackStrengthScale(.5f)>=.9f) { syncSlot(); sendAim(); mc.gameMode.attack(mc.player,entity); mc.player.swing(InteractionHand.MAIN_HAND); }
            return;
        }
        if(hover instanceof BlockHitResult block) {
            if(!mc.player.canReach(block.getBlockPos(),-.3)||!TacticalInteractionRules.visible(mc.level,mc.player,block.getLocation(),block.getBlockPos())) {
                mc.gameMode.stopDestroyBlock(); approach(block.getLocation(),block.getBlockPos(),mc.player.getBlockReach()); return;
            }
            PlayerCommandController.stop(); syncSlot(); sendAim();
            if(item.getItem() instanceof BlockItem) {
                mc.gameMode.stopDestroyBlock();
                if(mc.player.tickCount%4==0) { var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block); if(result.shouldSwing())mc.player.swing(InteractionHand.MAIN_HAND); }
            } else if(item.getItem() instanceof DiggerItem || item.isEmpty()) {
                mc.gameMode.continueDestroyBlock(block.getBlockPos(),block.getDirection()); mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.gameMode.stopDestroyBlock();
                if(mc.player.tickCount%4==0) {
                    var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block);
                    if(!result.consumesAction())result=mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                    if(result.shouldSwing())mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
        } else mc.gameMode.stopDestroyBlock();
    }
    public static net.minecraft.core.BlockPos placementPreview(HitResult hover) {
        var mc=Minecraft.getInstance();
        if(commands||mc.player==null||mc.level==null||!(mc.player.getMainHandItem().getItem() instanceof BlockItem)||!(hover instanceof BlockHitResult b))return null;
        var context=new net.minecraft.world.item.context.BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,mc.player.getMainHandItem(),b);
        return context.getClickedPos();
    }
    private static void approach(Vec3 point,net.minecraft.core.BlockPos block,double reach) {
        if(manualMovement()&&ClientConfig.controlMode()==ControlMode.ACTION) return;
        if(approachCooldown==0) { PlayerCommandController.approach(point,block,reach); approachCooldown=15; }
    }
    private static void syncSlot() { var mc=Minecraft.getInstance();mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().selected)); }
    private static void sendAim() { var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround())); }
    private static void aim(Vec3 point) {
        var p=Minecraft.getInstance().player; var d=point.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()))); p.setYHeadRot(p.getYRot());
    }
}
