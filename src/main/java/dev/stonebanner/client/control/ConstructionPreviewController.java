package dev.stonebanner.client.control;

import dev.stonebanner.construction.*;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.ConstructionActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.*;

/** Local geometry feedback only; placement and permissions belong to the server. */
public final class ConstructionPreviewController {
    private static BuildingBlueprint blueprint=BuildingBlueprint.cottage();
    public static BuildingBlueprint blueprint(){return blueprint;}
    private static boolean active;
    private static BlockPos origin;
    private static Rotation rotation=Rotation.NONE;
    private static ResourceLocation dimension;
    private static String status="site";
    private static long checkedAt=Long.MIN_VALUE;
    private ConstructionPreviewController(){}
    public static boolean active(){return active;}
    public static BlockPos origin(){return origin;}
    public static Rotation rotation(){return rotation;}
    public static String status(){return status;}
    public static boolean allowed(){return active&&origin!=null&&status.equals("ready");}
    public static void start(){start(BuildingBlueprint.cottage());}
    public static void start(BuildingBlueprint selected){
        if(selected==null)return;blueprint=selected;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        HeroInputController.cancel();PlayerCommandController.stop();DesignationController.deactivate();TunnelExtensionController.deactivate();
        active=true;origin=null;rotation=Rotation.NONE;dimension=mc.level.dimension().location();checkedAt=Long.MIN_VALUE;
    }
    public static void cancel(){active=false;origin=null;dimension=null;status="site";}
    public static void checkWorld(){
        var mc=Minecraft.getInstance();if(active&&(mc.level==null||mc.player==null||!mc.player.isAlive()||!mc.level.dimension().location().equals(dimension)))cancel();
    }
    public static void rotate(){rotation=Rotation.values()[(rotation.ordinal()+1)%4];checkedAt=Long.MIN_VALUE;}
    public static void update(HitResult hit){
        if(!active)return;var mc=Minecraft.getInstance();checkWorld();if(!active)return;
        BlockPos next=hit instanceof BlockHitResult block&&block.getDirection()==Direction.UP?block.getBlockPos().above():null;
        if(next==null){origin=null;status="site";return;}
        if(!next.equals(origin)||mc.level.getGameTime()-checkedAt>=5||checkedAt==Long.MIN_VALUE){
            origin=next;checkedAt=mc.level.getGameTime();status=mc.player.distanceToSqr(Vec3.atCenterOf(origin))>64*64?"site":ConstructionService.site(mc.level,origin,rotation,blueprint);
        }
    }
    public static void confirm(){
        if(!allowed())return;StoneBannerNetwork.sendConstructionAction(new ConstructionActionPacket(dimension,ConstructionService.Action.CREATE,origin,rotation.ordinal(),-1,blueprint.id(),blueprint.fingerprint()));cancel();
    }
}
