package dev.stonebanner.client.control;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.control.ScreenInputRules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Loaded, visible group selection; details use its first member. */
public final class CitizenSelectionController {
    private static final Set<Integer> ids=new LinkedHashSet<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final int MAX_SELECTION=64;
    private static void checkWorld() {var level=Minecraft.getInstance().level;if(level!=world){ids.clear();world=level;}}
    public static boolean select(Entity entity) {return select(entity,false);}
    public static boolean select(Entity entity,boolean additive) {
        checkWorld();
        if(!(entity instanceof HumanNpcEntity npc)||!npc.isAlive())return false;
        if(!additive)ids.clear();
        if(additive&&ids.contains(npc.getId()))ids.remove(npc.getId());else if(ids.size()<MAX_SELECTION)ids.add(npc.getId());
        return true;
    }
    public static List<HumanNpcEntity> selectedAll() {
        checkWorld();if(world==null)return List.of();
        ids.removeIf(id->!(world.getEntity(id) instanceof HumanNpcEntity n)||!n.isAlive());
        return ids.stream().map(id->(HumanNpcEntity)world.getEntity(id)).toList();
    }
    public static Optional<HumanNpcEntity> selected() {return selectedAll().stream().findFirst();}
    public static boolean hasSelection(){return !selectedAll().isEmpty();}
    public static void selectArea(double x1,double y1,double x2,double y2,int width,int height,boolean additive) {
        checkWorld();var mc=Minecraft.getInstance();if(world==null||dev.stonebanner.client.camera.RpgCameraController.viewObstructed())return;
        if(!additive)ids.clear();var camera=mc.gameRenderer.getMainCamera();
        double tangent=Math.tan(Math.toRadians(mc.gameRenderer.getFov(camera,mc.getFrameTime(),true))*.5);
        for(var e:world.entitiesForRendering()) {
            if(!(e instanceof HumanNpcEntity npc)||!npc.isAlive()||npc.distanceToSqr(mc.player)>256*256)continue;
            Vec3 center=npc.getBoundingBox().getCenter(),delta=center.subtract(camera.getPosition());
            double depth=delta.dot(new Vec3(camera.getLookVector()));if(depth<=0)continue;
            double x=width*.5*(1-delta.dot(new Vec3(camera.getLeftVector()))/(depth*tangent*width/height));
            double y=height*.5*(1-delta.dot(new Vec3(camera.getUpVector()))/(depth*tangent));
            if(!ScreenInputRules.inRectangle(x,y,x1,y1,x2,y2))continue;
            var hit=world.clip(new net.minecraft.world.level.ClipContext(camera.getPosition(),center,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS&&hit.getLocation().distanceToSqr(camera.getPosition())<delta.lengthSqr()-.05)continue;
            if(ids.size()<MAX_SELECTION)ids.add(npc.getId());
        }
    }
    public static boolean workSelected(BlockHitResult hit) {
        var all=selectedAll();var mc=Minecraft.getInstance();if(all.isEmpty()||mc.level==null)return false;
        var state=mc.level.getBlockState(hit.getBlockPos());
        if(!state.is(net.minecraft.tags.BlockTags.LOGS)&&!state.is(net.minecraftforge.common.Tags.Blocks.ORES))return false;
        for(var npc:all)StoneBannerNetwork.sendCitizenWorkTarget(npc.getId(),hit.getBlockPos());return true;
    }
    public static boolean moveSelected(BlockHitResult hit) {
        var npcs=selectedAll();if(npcs.isEmpty())return false;
        var face=hit.getDirection();BlockPos target=face==Direction.UP?hit.getBlockPos().above():hit.getBlockPos().relative(face);
        for(var npc:npcs)StoneBannerNetwork.sendMoveCitizen(npc.getId(),target);
        return true;
    }
    public static void commandTarget(Entity entity) {
        for(var npc:selectedAll()) StoneBannerNetwork.sendCitizenTarget(npc.getId(),entity.getId());
    }
    public static boolean stopAndClear(){var all=selectedAll();for(var npc:all)StoneBannerNetwork.sendStopCitizen(npc.getId());clear();return !all.isEmpty();}
    public static void clear(){ids.clear();}
}
