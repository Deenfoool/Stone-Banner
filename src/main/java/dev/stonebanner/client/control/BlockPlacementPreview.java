package dev.stonebanner.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Advisory client geometry; actual use still goes through vanilla server placement. */
public record BlockPlacementPreview(BlockPos pos,BlockState state,boolean allowed) {
    private static Float yaw;
    private static net.minecraft.world.item.Item item;
    public static void reset(){yaw=null;item=null;}
    public static boolean rotate(int direction){
        var mc=Minecraft.getInstance();
        if(HeroInputController.commandMode() || DesignationController.isActive() || TunnelExtensionController.isActive()
                || !(mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen screen)
                || !(screen.hoveredHit() instanceof BlockHitResult) || mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof BlockItem))return false;
        if(item!=mc.player.getMainHandItem().getItem()){reset();item=mc.player.getMainHandItem().getItem();}
        yaw=(yaw==null?mc.player.getYRot():yaw)+(direction<0?-90:90);return true;
    }
    public static void orient(){
        var p=Minecraft.getInstance().player;
        if(p!=null && item==p.getMainHandItem().getItem()&&yaw!=null){p.setYRot(yaw);p.setYHeadRot(yaw);}
    }
    public static BlockPlacementPreview at(HitResult hit){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||HeroInputController.commandMode()
                || !(hit instanceof BlockHitResult b)||!(mc.player.getMainHandItem().getItem() instanceof BlockItem block))return null;
        if(item!=mc.player.getMainHandItem().getItem())reset();
        float previous=mc.player.getYRot();orient();
        BlockPlaceContext context=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,mc.player.getMainHandItem(),b);
        BlockPos at=context.getClickedPos();
        BlockState state=block.getBlock().getStateForPlacement(context);
        mc.player.setYRot(previous);mc.player.setYHeadRot(previous);
        if(state==null)state=block.getBlock().defaultBlockState();
        boolean allowed=context.canPlace()&&mc.level.hasChunkAt(at)&&!mc.level.isOutsideBuildHeight(at)&&mc.level.getWorldBorder().isWithinBounds(at)
                && mc.player.mayBuild()&&mc.level.mayInteract(mc.player,at)&&state.canSurvive(mc.level,at)
                &&mc.level.isUnobstructed(state,at,CollisionContext.of(mc.player))
                &&mc.player.canReach(b.getBlockPos(),-.3)
                &&dev.stonebanner.control.TacticalInteractionRules.visible(mc.level,mc.player,b.getLocation(),b.getBlockPos());
        return new BlockPlacementPreview(at.immutable(),state,allowed);
    }
}
