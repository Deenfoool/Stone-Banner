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
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;
    private static int selectedSlot = -1, approachCooldown;
    private static HitResult target, lockedTarget;
    private static Vec3 actionOrigin;
    private static int actionStarted;
    private static net.minecraft.world.level.block.state.BlockState lockedState;
    private static java.util.UUID lockedEntity;
    public static float miningProgress(){
        var mc=Minecraft.getInstance();
        return actionHeld && attackIntent && target instanceof BlockHitResult b && mc.gameMode!=null
                && b.getBlockPos().equals(mc.gameMode.destroyBlockPos)?mc.gameMode.destroyProgress:0;
    }
    private static final dev.stonebanner.control.GroundClickTracker groundClicks=new dev.stonebanner.control.GroundClickTracker();
    private static long lastGroundPressTick=-1;
    public static void resetGroundClicks(){groundClicks.reset();lastGroundPressTick=-1;}
    public static dev.stonebanner.control.MovementPace groundClickPace(boolean plainGround,double x,double y) {
        var mc=Minecraft.getInstance();
        if(mc.player.tickCount==lastGroundPressTick)return PlayerCommandController.movePace();
        lastGroundPressTick=mc.player.tickCount;
        if(!plainGround || descend()) {
            groundClicks.reset();return descend()?dev.stonebanner.control.MovementPace.CAREFUL:dev.stonebanner.control.MovementPace.WALK;
        }
        boolean twice=groundClicks.click(net.minecraft.Util.getMillis(),x,y,ClientConfig.DOUBLE_CLICK_MS.get());
        return InputBindings.held(mc.options.keySprint) || twice && ClientConfig.DOUBLE_CLICK_RUN.get()
                ? dev.stonebanner.control.MovementPace.RUN : dev.stonebanner.control.MovementPace.WALK;
    }
    public static boolean commandMode() { return commands; }
    private static net.minecraft.client.KeyMapping heldBinding, completedBinding;
    private static boolean attackIntent;
    public static boolean jump() { return InputBindings.held(Minecraft.getInstance().options.keyJump); }
    public static boolean descend() { return InputBindings.held(Minecraft.getInstance().options.keyShift); }
    public static boolean manualMovement() { var o=Minecraft.getInstance().options; return InputBindings.held(o.keyUp)||InputBindings.held(o.keyLeft)||InputBindings.held(o.keyDown)||InputBindings.held(o.keyRight); }
    public static void toggleCommands() {
        resetGroundClicks();cancel(); PlayerCommandController.cancelPendingActions();commands=!commands;
        // Keep the selected NPCs on Hero/Orders transitions. Only transient tool previews
        // reset; the server continues authoritative queued movement/work orders.
        DesignationController.deactivate(); TunnelExtensionController.deactivate();
        RpgCameraController.commandsChanged(commands);
    }
    public static boolean moveHeld(){return moveHeld;}
    public static void setMoveHeld(boolean held) { moveHeld=held; }
    public static void setActionHeld(boolean held) {
        if(!held) { cancelAction(); return; }
        setActionHeld(Minecraft.getInstance().options.keyUse, false);
    }
    public static void setActionHeld(net.minecraft.client.KeyMapping binding, boolean attack) {
        setActionHeld(binding,attack,null);
    }
    public static void setActionHeld(net.minecraft.client.KeyMapping binding, boolean attack, HitResult hit) {
        if(completedBinding==binding && InputBindings.held(binding))return;
        if (actionHeld && heldBinding==binding && attackIntent==attack) return;
        cancelAction(false); moveHeld=false; PlayerCommandController.stop(); heldBinding=binding; attackIntent=attack; actionHeld=true;
        var mc=Minecraft.getInstance();lockedTarget=hit;actionOrigin=mc.player.position();actionStarted=mc.player.tickCount;
        lockedState=hit instanceof BlockHitResult b?mc.level.getBlockState(b.getBlockPos()):null;
        lockedEntity=hit instanceof EntityHitResult e?e.getEntity().getUUID():null;
    }
    public static void release(net.minecraft.client.KeyMapping binding) {
        if (actionHeld && heldBinding == binding) cancelAction(true);
        if(completedBinding==binding)completedBinding=null;
    }
    public static void cancel() { moveHeld=false; cancelAction(false); }
    private static void cancelAction() { cancelAction(true); }
    private static void cancelAction(boolean release) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.gameMode!=null) {
            if(mc.player.isUsingItem()) {
                if (release) {
                    if(target!=null)aim(target instanceof EntityHitResult e?e.getEntity().getBoundingBox().getCenter():target.getLocation());
                    sendAim(); mc.gameMode.releaseUsingItem(mc.player);
                } else {
                    // Slot change aborts a bow on the server without firing a release shot.
                    int slot=mc.player.getInventory().selected;
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket((slot+1)%9));
                    mc.player.stopUsingItem();
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                }
            }
            mc.gameMode.stopDestroyBlock();
        }
        if(actionHeld) {completedBinding=heldBinding;PlayerCommandController.stop();}
        actionHeld=false; heldBinding=null; target=null; lockedTarget=null;lockedState=null;lockedEntity=null;actionOrigin=null;approachCooldown=0;
    }
    public static void tick(HitResult hover, boolean overUi, HitResult movementHit) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.gameMode==null) { cancel(); commands=false; tracked=null; return; }
        if(tracked!=mc.player || trackedWorld!=mc.level) { resetGroundClicks();cancel(); commands=false; tracked=mc.player;trackedWorld=mc.level; selectedSlot=mc.player.getInventory().selected;BlockPlacementPreview.reset(); }
        if(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)!=1) { cancel(); return; }
        if(commands||overUi||mc.player.isSpectator()||!mc.player.isAlive()) { cancel(); return; }
        if(completedBinding!=null && !InputBindings.held(completedBinding))completedBinding=null;
        if(actionHeld && (heldBinding==null || !InputBindings.held(heldBinding)))cancelAction();
        if(moveHeld && !InputBindings.held(mc.options.keyAttack))moveHeld=false;
        if(approachCooldown>0)approachCooldown--;
        if(selectedSlot!=mc.player.getInventory().selected) { cancelAction(false); selectedSlot=mc.player.getInventory().selected;BlockPlacementPreview.reset(); }
        if(moveHeld && ClientConfig.controlMode()==ControlMode.HYBRID && movementHit instanceof BlockHitResult block)
            PlayerCommandController.updateHeldMove(block);
        if(!actionHeld||overUi||RpgCameraController.viewObstructed()) { if((overUi||RpgCameraController.viewObstructed())&&actionHeld)cancelAction(false); return; }
        var item=mc.player.getMainHandItem();
        boolean using=!attackIntent && item.getUseDuration()>0;
        if(!using && lockedTarget!=null)hover=lockedTarget;
        target=hover;
        boolean approaching=hover instanceof BlockHitResult b && (!mc.player.canReach(b.getBlockPos(),-.3)
                || !TacticalInteractionRules.visible(mc.level,mc.player,b.getLocation(),b.getBlockPos()));
        if((attackIntent && hover instanceof EntityHitResult || !using && approaching)
                && !dev.stonebanner.control.HeroActionBudget.permits(mc.player.tickCount-actionStarted,
                    mc.player.position().distanceToSqr(actionOrigin),mc.player.position().distanceToSqr(
                        hover instanceof EntityHitResult e?e.getEntity().position():hover.getLocation()))) {cancelAction(false);return;}
        if(attackIntent && hover instanceof BlockHitResult b && (!mc.level.hasChunkAt(b.getBlockPos())
                || lockedState!=null&&!lockedState.equals(mc.level.getBlockState(b.getBlockPos())))) {cancelAction(false);return;}
        if(hover!=null) {
            Vec3 point=hover instanceof EntityHitResult e?e.getEntity().getBoundingBox().getCenter():hover.getLocation();
            if(attackIntent && hover instanceof EntityHitResult)softAim(point);else aim(point);
        }
        if(using) {
            mc.gameMode.stopDestroyBlock();
            if(!mc.player.isUsingItem()) { sendAim(); syncSlot(); mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND); }
            return;
        }
        if(hover instanceof EntityHitResult entityHit) {
            mc.gameMode.stopDestroyBlock(); var entity=entityHit.getEntity();
            if(!attackIntent)return;
            if(!dev.stonebanner.control.HeroActionRules.canAttack(mc.player,entity)
                    || lockedEntity!=null&&!lockedEntity.equals(entity.getUUID()) || mc.level.getEntity(entity.getId())!=entity) {cancelAction(false);return;}
            if(!mc.player.canReach(entity,-.3)||!TacticalInteractionRules.visible(mc.level,mc.player,entity.getBoundingBox().getCenter(),null)) {
                if(ClientConfig.controlMode()!=ControlMode.ACTION)approach(entity.getBoundingBox().getCenter(),null,mc.player.getEntityReach()); return;
            }
            PlayerCommandController.stop();
            if(facing(entity.getBoundingBox().getCenter()) && mc.player.getAttackStrengthScale(.5f)>=.9f) { syncSlot(); sendAim(); mc.gameMode.attack(mc.player,entity); mc.player.swing(InteractionHand.MAIN_HAND); }
            return;
        }
        if(hover instanceof BlockHitResult block) {
            if(!mc.player.canReach(block.getBlockPos(),-.3)||!TacticalInteractionRules.visible(mc.level,mc.player,block.getLocation(),block.getBlockPos())) {
                mc.gameMode.stopDestroyBlock(); if(ClientConfig.controlMode()!=ControlMode.ACTION)approach(block.getLocation(),block.getBlockPos(),mc.player.getBlockReach()); return;
            }
            PlayerCommandController.stop(); syncSlot();
            if(!attackIntent && item.getItem() instanceof BlockItem)BlockPlacementPreview.orient();
            sendAim();
            if(!attackIntent && item.getItem() instanceof BlockItem) {
                mc.gameMode.stopDestroyBlock();
                var preview=BlockPlacementPreview.at(block);
                if(mc.player.tickCount%4==0 && preview!=null && preview.allowed()) { var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block); if(result.shouldSwing())mc.player.swing(InteractionHand.MAIN_HAND); }
            } else if(attackIntent) {
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
    private static void approach(Vec3 point,net.minecraft.core.BlockPos block,double reach) {
        if (!ClientConfig.AUTO_APPROACH.get()) { cancelAction(false); return; }
        if(manualMovement()&&ClientConfig.controlMode()==ControlMode.ACTION) return;
        if(approachCooldown==0) { PlayerCommandController.approach(point,block,reach); approachCooldown=15; }
    }
    private static void syncSlot() { var mc=Minecraft.getInstance();mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().selected)); }
    private static void sendAim() { var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround())); }
    private static boolean facing(Vec3 point){
        var p=Minecraft.getInstance().player;var d=point.subtract(p.getEyePosition());
        return Math.abs(net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-d.x,d.z))-p.getYRot()))<20;
    }
    private static void softAim(Vec3 point){
        var p=Minecraft.getInstance().player;var d=point.subtract(p.getEyePosition());
        p.setYRot(dev.stonebanner.control.HeroActionBudget.turn(p.getYRot(),(float)Math.toDegrees(Math.atan2(-d.x,d.z)),12));
        p.setYHeadRot(p.getYRot());
    }
    private static void aim(Vec3 point) {
        var p=Minecraft.getInstance().player; var d=point.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()))); p.setYHeadRot(p.getYRot());
    }
}
