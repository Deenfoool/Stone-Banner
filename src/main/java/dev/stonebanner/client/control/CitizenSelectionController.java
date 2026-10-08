package dev.stonebanner.client.control;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.control.ScreenInputRules;
import dev.stonebanner.control.CitizenControlGroups;
import dev.stonebanner.StoneAndBanner;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Loaded, visible group selection; details use its first member. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class CitizenSelectionController {
    private static final Map<Integer, UUID> ids=new LinkedHashMap<>();
    private static final CitizenControlGroups groups = new CitizenControlGroups();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final int MAX_SELECTION=64;
    private static void checkWorld() {var level=Minecraft.getInstance().level;if(level!=world){ids.clear();groups.clear();world=level;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ids.clear(); groups.clear(); world = null;
    }
    public static boolean select(Entity entity) {return select(entity,false);}
    public static boolean select(Entity entity,boolean additive) {
        checkWorld();
        if(!(entity instanceof HumanNpcEntity npc)||!npc.isAlive())return false;
        if(!additive)ids.clear();
        if(additive&&npc.getUUID().equals(ids.get(npc.getId())))ids.remove(npc.getId());else if(ids.size()<MAX_SELECTION)ids.put(npc.getId(),npc.getUUID());
        return true;
    }
    public static List<HumanNpcEntity> selectedAll() {
        checkWorld();if(world==null)return List.of();
        ids.entrySet().removeIf(entry->!(world.getEntity(entry.getKey()) instanceof HumanNpcEntity n)||!n.isAlive()||!n.getUUID().equals(entry.getValue()));
        return ids.keySet().stream().map(id->(HumanNpcEntity)world.getEntity(id)).toList();
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
            if(ids.size()<MAX_SELECTION)ids.put(npc.getId(),npc.getUUID());
        }
    }

    public static void saveGroup(int slot) {
        var selected = selectedAll();
        groups.save(slot, selected.stream().map(Entity::getUUID).toList());
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(selected.isEmpty()
                ? Component.translatable("message.stonebanner.group_cleared", slot + 1)
                : Component.translatable("message.stonebanner.group_saved", slot + 1, selected.size()), true);
    }

    public static List<HumanNpcEntity> recallGroup(int slot, boolean additive) {
        checkWorld();
        if (world == null) return List.of();
        var loaded = new HashMap<UUID, HumanNpcEntity>();
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof HumanNpcEntity npc && npc.isAlive()) loaded.put(npc.getUUID(), npc);
        }
        var available = groups.members(slot).stream().map(loaded::get).filter(Objects::nonNull).toList();
        // Empty or temporarily unloaded groups do not erase the player's current selection.
        if (!available.isEmpty()) {
            selectedAll();
            if (!additive) ids.clear();
            for (var npc : available) {
                if (ids.size() < MAX_SELECTION) ids.put(npc.getId(), npc.getUUID());
            }
        }
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(
                "message.stonebanner.group_recalled", slot + 1, available.size(), groups.members(slot).size()), true);
        return available;
    }
    public static boolean workSelected(BlockHitResult hit) { return workSelected(hit,false); }
    public static boolean workSelected(BlockHitResult hit, boolean append) {
        var all=selectedAll();var mc=Minecraft.getInstance();if(all.isEmpty()||mc.level==null)return false;
        var state=mc.level.getBlockState(hit.getBlockPos());
        if(!state.is(net.minecraft.tags.BlockTags.LOGS)&&!state.is(net.minecraftforge.common.Tags.Blocks.ORES))return false;
        for(var npc:all)StoneBannerNetwork.sendCitizenWorkTarget(npc.getId(),hit.getBlockPos(),append);return true;
    }
    public static boolean moveSelected(BlockHitResult hit) {
        return moveSelected(hit, false);
    }
    public static boolean moveSelected(BlockHitResult hit, boolean append) {
        var npcs=selectedAll();if(npcs.isEmpty())return false;
        var face=hit.getDirection();BlockPos target=face==Direction.UP?hit.getBlockPos().above():hit.getBlockPos().relative(face);
        for(var npc:npcs) {
            if (append) StoneBannerNetwork.sendQueuedMoveCitizen(npc.getId(), target);
            else StoneBannerNetwork.sendMoveCitizen(npc.getId(),target);
        }
        return true;
    }
    public static void commandTarget(Entity entity) { commandTarget(entity,false); }
    public static void commandTarget(Entity entity, boolean append) {
        for(var npc:selectedAll()) StoneBannerNetwork.sendCitizenTarget(npc.getId(),entity.getId(),append);
    }
    public static boolean stopAndClear(){var all=selectedAll();for(var npc:all)StoneBannerNetwork.sendStopCitizen(npc.getId());clear();return !all.isEmpty();}
    public static void clear(){ids.clear();}
}
