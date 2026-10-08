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
    public enum Action { OPEN, REFRESH, CREATE, PAUSE, RESUME, CANCEL }
    private ConstructionService(){}
    public static String site(Level level,BlockPos origin,Rotation rotation){
        var bounds=CottageBlueprint.bounds(origin,rotation);
        if(bounds.minY<=level.getMinBuildHeight()||bounds.maxY>level.getMaxBuildHeight())return "site";
        var expected=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var placement:CottageBlueprint.placements())for(var cell:placement.cells())expected.put(cell.at(origin,rotation),cell.oriented(rotation));
        for(var p:BlockPos.betweenClosed(new BlockPos((int)bounds.minX,origin.getY(),(int)bounds.minZ),new BlockPos((int)bounds.maxX-1,origin.getY()+4,(int)bounds.maxZ-1))){
            if(!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p))return "unloaded";
            var state=level.getBlockState(p);var desired=expected.get(p);
            if(!state.getFluidState().isEmpty())return "site";
            if(desired==null || !CottageBlueprint.same(state,desired)){
                if(!state.canBeReplaced()||level.getBlockEntity(p)!=null)return "site";
            }
            if(p.getY()==origin.getY()&&!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))return "foundation";
        }
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds,e->e.isAlive()&&!e.isSpectator()).isEmpty())return "occupied";
        return "ready";
    }
    public static Optional<CottageBlueprint.Placement> next(ServerLevel level,ConstructionData.Plan plan){
        if(plan==null||plan.paused||plan.completed)return Optional.empty();
        for(var placement:CottageBlueprint.placements())if(!placement.matches(level,plan.origin,plan.rotation))return Optional.of(placement);
        ConstructionData.forLevel(level).complete(plan);return Optional.empty();
    }
    public static boolean valid(ServerLevel level,CitizenJob job){
        var p=ConstructionData.forLevel(level).at(job.target());return p!=null&&!p.paused&&!p.completed;
    }
    public static boolean allowed(HumanNpcEntity npc,CitizenJob job){
        var p=ConstructionData.forLevel((ServerLevel)npc.level()).at(job.target());
        return p!=null&&!p.paused&&!p.completed&&npc.citizenData().recruitedBy().map(p.owner::equals).orElse(true);
    }
    public static void execute(ServerPlayer player,Action action,BlockPos origin,int rotation,long id){
        if(!player.isAlive()||player.isSpectator())return;
        var level=player.serverLevel();var data=ConstructionData.forLevel(level);String result="ready";
        if(action==Action.CREATE){
            if(rotation<0||rotation>=Rotation.values().length||player.distanceToSqr(Vec3.atCenterOf(origin))>64*64)result="site";
            else{
                var orientation=Rotation.values()[rotation];result=site(level,origin,orientation);
                var bounds=CottageBlueprint.bounds(origin,orientation);
                if(result.equals("ready"))for(var p:BlockPos.betweenClosed(new BlockPos((int)bounds.minX,origin.getY(),(int)bounds.minZ),new BlockPos((int)bounds.maxX-1,origin.getY()+4,(int)bounds.maxZ-1))){
                    if(!level.mayInteract(player,p)||SettlementData.forLevel(level).communities().stream().anyMatch(c->c.contains(p)&&!c.owner().equals(player.getUUID()))){result="forbidden";break;}
                }
                if(result.equals("ready")&&dev.stonebanner.designation.ExcavationLadderTaskData.forLevel(level).task(origin).isPresent())result="overlap";
                if(result.equals("ready"))result=data.add(player.getUUID(),origin,orientation)>0?"created":"overlap";
            }
        }else if(action==Action.PAUSE||action==Action.RESUME||action==Action.CANCEL){
            result=data.edit(player.getUUID(),id,action.name().toLowerCase(Locale.ROOT))?"updated":"forbidden";
        }
        reconcile(level);
        StoneBannerNetwork.sendConstruction(player,ConstructionSnapshotPacket.forPlayer(player,action==Action.OPEN,result));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level&&level.getGameTime()%100==0)reconcile(level);
    }
    public static void reconcile(ServerLevel level){
        var data=ConstructionData.forLevel(level);var board=CitizenJobBoard.forLevel(level);
        for(var job:board.snapshot())if(job.workType()==WorkType.BUILDING
                &&dev.stonebanner.designation.ExcavationLadderTaskData.forLevel(level).task(job.target()).isEmpty()&&!valid(level,job))board.remove(job.id());
        for(var plan:data.plans())if(!plan.paused&&!plan.completed){
            if(next(level,plan).isPresent())board.publish(WorkType.BUILDING,plan.origin,CitizenSkill.CONSTRUCTION,0,level.getGameTime());
        }
    }
}
