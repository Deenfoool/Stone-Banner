package dev.stonebanner.construction;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.ConstructionSnapshotPacket;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class ConstructionService {
    public enum Action { OPEN, REFRESH, SELECT, CREATE, PAUSE, RESUME, CANCEL }
    private ConstructionService(){}
    public static String site(Level level,BlockPos origin,Rotation rotation){return site(level,origin,rotation,BuildingBlueprint.cottage());}
    public static String site(Level level,BlockPos origin,Rotation rotation,BuildingBlueprint blueprint){
        var bounds=blueprint.bounds(origin,rotation);
        if(bounds.minY<=level.getMinBuildHeight()||bounds.maxY>level.getMaxBuildHeight())return "site";
        var expected=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var placement:blueprint.placements())for(var cell:placement.cells())expected.put(cell.at(origin,rotation),cell.oriented(rotation));
        for(var p:BlockPos.betweenClosed(new BlockPos((int)bounds.minX,origin.getY(),(int)bounds.minZ),new BlockPos((int)bounds.maxX-1,origin.getY()+blueprint.sizeY()-1,(int)bounds.maxZ-1))){
            if(!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p))return "unloaded";
            if(blueprint.preserved().contains(p.subtract(origin).rotate(inverse(rotation))))continue;
            var state=level.getBlockState(p);var desired=expected.get(p);
            if(!state.getFluidState().isEmpty())return "site";
            if(desired==null || !CottageBlueprint.same(state,desired)){
                if(!state.canBeReplaced()||level.getBlockEntity(p)!=null)return "site";
            }
            if(desired!=null&&p.getY()==origin.getY()&&!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))return "foundation";
        }
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds,e->e.isAlive()&&!e.isSpectator()).isEmpty())return "occupied";
        return "ready";
    }
    public static Optional<CottageBlueprint.Placement> next(ServerLevel level,ConstructionData.Plan plan){
        if(plan==null||plan.paused||plan.completed||plan.cancelled)return Optional.empty();
        // A missing chunk is neither a matching placement nor a finished structure.
        if (!worksiteLoaded(level, plan)) return Optional.empty();
        var blueprint=BlueprintCatalog.forPlan(level,plan);if(blueprint==null)return Optional.empty();
        for(var placement:blueprint.placements())if(!placement.matches(level,plan.origin,plan.rotation))return Optional.of(placement);
        if(plan.temporary.isEmpty())ConstructionData.forLevel(level).complete(plan);else{plan.cleanup=true;plan.status="scaffold_cleanup";ConstructionData.forLevel(level).setDirty();}return Optional.empty();
    }

    /**
     * No construction plan is allowed to inspect blocks in an unloaded area. Blueprints
     * may cover several chunk columns, and their temporary scaffold routes can extend
     * outside the footprint. Only inspect loaded terrain; do not request chunk tickets.
     */
    public static boolean worksiteLoaded(ServerLevel level, ConstructionData.Plan plan) {
        if (plan == null) return false;
        var bounds = plan.bounds();
        int minChunkX = ((int) Math.floor(bounds.minX)) >> 4;
        int maxChunkX = (((int) Math.ceil(bounds.maxX)) - 1) >> 4;
        int minChunkZ = ((int) Math.floor(bounds.minZ)) >> 4;
        int maxChunkZ = (((int) Math.ceil(bounds.maxZ)) - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunkAt(new BlockPos(chunkX << 4, plan.origin.getY(), chunkZ << 4)))
                    return false;
            }
        }
        for (BlockPos temporary : plan.temporary.keySet())
            if (!level.hasChunkAt(temporary)) return false;
        if (plan.route != null)
            for (BlockPos routeBlock : plan.route.blocks().keySet())
                if (!level.hasChunkAt(routeBlock)) return false;
        return true;
    }

    public static boolean valid(ServerLevel level,CitizenJob job){
        var p=ConstructionData.forLevel(level).at(job.target());return p!=null&&!p.paused&&!p.completed&&(p.cancelled||!p.temporary.isEmpty()||BlueprintCatalog.forPlan(level,p)!=null);
    }
    public static boolean allowed(HumanNpcEntity npc,CitizenJob job){
        var p=ConstructionData.forLevel((ServerLevel)npc.level()).at(job.target());
        return p!=null&&!p.paused&&!p.completed&&(p.cancelled||!p.temporary.isEmpty()||BlueprintCatalog.forPlan((ServerLevel)npc.level(),p)!=null)&&npc.citizenData().recruitedBy().map(p.owner::equals).orElse(true);
    }
    public static void execute(ServerPlayer player,Action action,BlockPos origin,int rotation,long id){execute(player,action,origin,rotation,id,"stonebanner:cottage","cottage-v1");}
    public static void execute(ServerPlayer player,Action action,BlockPos origin,int rotation,long id,String blueprintId,String fingerprint){
        if(!player.isAlive()||player.isSpectator())return;
        var level=player.serverLevel();var data=ConstructionData.forLevel(level);String result="ready";
        var blueprint=BlueprintCatalog.forLevel(level).get(blueprintId);
        if(action==Action.SELECT&&blueprint==null)result="unsupported";
        if(action==Action.CREATE){
            if(blueprint==null||!blueprint.fingerprint().equals(fingerprint)){StoneBannerNetwork.sendConstruction(player,ConstructionSnapshotPacket.forPlayer(player,false,"blueprint_changed",blueprintId));return;}
            if(rotation<0||rotation>=Rotation.values().length||player.distanceToSqr(Vec3.atCenterOf(origin))>64*64)result="site";
            else{
                var orientation=Rotation.values()[rotation];result=site(level,origin,orientation,blueprint);
                var bounds=blueprint.bounds(origin,orientation);
                if(result.equals("ready"))for(var p:BlockPos.betweenClosed(new BlockPos((int)bounds.minX,origin.getY(),(int)bounds.minZ),new BlockPos((int)bounds.maxX-1,origin.getY()+blueprint.sizeY()-1,(int)bounds.maxZ-1))){
                    if(!level.mayInteract(player,p)||SettlementData.forLevel(level).communities().stream().anyMatch(c->c.contains(p)&&!c.owner().equals(player.getUUID()))){result="forbidden";break;}
                }
                if(result.equals("ready")&&dev.stonebanner.designation.ExcavationLadderTaskData.forLevel(level).task(origin).isPresent())result="overlap";
                if(result.equals("ready"))result=data.add(player.getUUID(),origin,orientation,blueprint)>0?"created":"overlap";
            }
        }else if(action==Action.PAUSE||action==Action.RESUME||action==Action.CANCEL){
            result=data.edit(player.getUUID(),id,action.name().toLowerCase(Locale.ROOT))?"updated":"forbidden";
        }
        reconcile(level);
        StoneBannerNetwork.sendConstruction(player,ConstructionSnapshotPacket.forPlayer(player,action==Action.OPEN,result,blueprintId));
    }
    private static Rotation inverse(Rotation rotation){return switch(rotation){case CLOCKWISE_90->Rotation.COUNTERCLOCKWISE_90;case COUNTERCLOCKWISE_90->Rotation.CLOCKWISE_90;default->rotation;};}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level&&level.getGameTime()%100==0)reconcile(level);
    }
    public static void reconcile(ServerLevel level){
        var data=ConstructionData.forLevel(level);var board=CitizenJobBoard.forLevel(level);
        for (var job : board.snapshot()) {
            if (job.workType() != WorkType.BUILDING || !level.hasChunkAt(job.target())) continue;
            if (dev.stonebanner.designation.ExcavationLadderTaskData.forLevel(level).task(job.target()).isEmpty()
                    && !valid(level, job)) board.remove(job.id());
        }
        for (var plan : data.plans()) {
            if (plan.paused || plan.completed || !worksiteLoaded(level, plan)) continue;
            if (next(level, plan).isPresent() || !plan.temporary.isEmpty() || plan.cancelled)
                board.publish(WorkType.BUILDING, plan.origin, CitizenSkill.CONSTRUCTION, 0, level.getGameTime());
        }
    }
}
