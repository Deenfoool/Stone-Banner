package dev.stonebanner.construction;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.storage.StorageData;
import dev.stonebanner.storage.CitizenStorageAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** One reserved plan, one physical item and one bounded placement at a time. No virtual material balance. */
public final class CitizenConstructionController {
    public enum Result { RUNNING, COMPLETE, DEFER }
    private final HumanNpcEntity npc;
    private final ConstructionScaffoldController scaffold;
    private BlockPos source,target;
    private double progress;
    private boolean working;
    private WorkBlockReason reason=WorkBlockReason.NONE;
    public CitizenConstructionController(HumanNpcEntity npc){this.npc=npc;this.scaffold=new ConstructionScaffoldController(npc);}
    public void clear(){scaffold.clear();source=target=null;progress=0;working=false;reason=WorkBlockReason.NONE;}
    public boolean working(){return working||scaffold.working();}
    public WorkBlockReason reason(){return reason;}
    private boolean visible(ServerLevel level,BlockPos pos,Vec3 eyes){
        for(var p:BlockPos.betweenClosed(BlockPos.containing(Math.min(eyes.x,pos.getX()),Math.min(eyes.y,pos.getY()),Math.min(eyes.z,pos.getZ())),
                BlockPos.containing(Math.max(eyes.x,pos.getX()),Math.max(eyes.y,pos.getY()),Math.max(eyes.z,pos.getZ()))))if(!level.hasChunkAt(p))return false;
        var hit=level.clip(new net.minecraft.world.level.ClipContext(eyes,Vec3.atCenterOf(pos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(pos);
    }
    private boolean near(ServerLevel level,BlockPos pos){return npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<=3.5*3.5&&visible(level,pos,npc.getEyePosition());}
    private boolean overlaps(ServerLevel level,ConstructionData.Plan plan,CottageBlueprint.Placement placement,AABB body){
        for(var cell:placement.cells())for(var shape:cell.oriented(plan.rotation).getCollisionShape(level,cell.at(plan.origin,plan.rotation)).toAabbs())
            if(shape.move(cell.at(plan.origin,plan.rotation)).intersects(body))return true;
        return false;
    }
    private boolean walk(ServerLevel level,BlockPos pos,ConstructionData.Plan plan,CottageBlueprint.Placement placement){
        var candidates=new ArrayList<BlockPos>();int base=pos.getY();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=base-3;y<=base+1;y++){
            var p=new BlockPos(pos.getX()+dx,y,pos.getZ()+dz);
            if(!level.hasChunkAt(p)||!npc.citizenData().canTravelTo(p)||!BlockPathfinder.isWalkable(level,p))continue;
            var feet=BlockPathfinder.waypoint(level,p);var eyes=feet.add(0,npc.getEyeHeight(),0);
            var body=npc.getBoundingBox().move(feet.subtract(npc.position()));
            if(eyes.distanceToSqr(Vec3.atCenterOf(pos))<=3.5*3.5&&visible(level,pos,eyes)
                    &&(placement==null||(placement.cells().stream().allMatch(c->eyes.distanceToSqr(Vec3.atCenterOf(c.at(plan.origin,plan.rotation)))<=3.5*3.5&&visible(level,c.at(plan.origin,plan.rotation),eyes))&&!overlaps(level,plan,placement,body))))candidates.add(p);
        }
        candidates.sort(Comparator.comparingDouble(p->npc.distanceToSqr(Vec3.atCenterOf(p))));
        for(var p:candidates.stream().limit(8).toList()){
            var feet=BlockPathfinder.waypoint(level,p);
            // A center-point ray can graze a wall corner while the NPC's actual eyes
            // remain occluded a few millimetres away. Navigation considers this cell
            // reached already; issuing it again forever cannot improve visibility.
            // Choose another standing cell instead of treating that no-op as progress.
            if(npc.distanceToSqr(feet)<=.1*.1)continue;
            if(npc.distanceToSqr(feet)<.55*.55&&npc.distanceToSqr(feet)>.1*.1){
                npc.commandController().stop();npc.getMoveControl().setWantedPosition(feet.x,feet.y,feet.z,.5);return true;
            }
            if(npc.commandController().issueSystemMove(p,CitizenBrainState.WORK))return true;
        }
        return false;
    }
    private Result fetch(ServerLevel level,net.minecraft.world.item.Item item,ConstructionData.Plan plan){
        var blueprint=BlueprintCatalog.forPlan(level,plan);if(blueprint==null)return Result.DEFER;
        int needed=blueprint.placements().stream().filter(p->p.item()==item&&!p.matches(level,plan.origin,plan.rotation)).mapToInt(CottageBlueprint.Placement::count).sum();
        return fetch(level,item,plan,needed);
    }
    private Result fetch(ServerLevel level,net.minecraft.world.item.Item item,ConstructionData.Plan plan,int required){
        if(plan.route!=null&&!scaffold.onGroundLevel(plan)){
            var result=scaffold.descend(plan);reason=scaffold.reason();return result==Result.COMPLETE?Result.RUNNING:result;
        }
        var stores=StorageData.forLevel(level);
        if(source!=null){
            if(!CitizenStorageAccess.mayUse(npc,source)){reason=WorkBlockReason.MATERIALS;return Result.DEFER;}
            if(!level.hasChunkAt(source)||!npc.citizenData().canTravelTo(source)){reason=WorkBlockReason.NO_PATH;return Result.DEFER;}
            if(!near(level,source)){
                if(!npc.commandController().hasActiveCommand()){reason=WorkBlockReason.NO_PATH;return Result.DEFER;}return Result.RUNNING;
            }
            npc.commandController().stop();
            int needed=Math.max(1,required-npc.citizenData().inventory().countPersonalItem(item));
            var removed=stores.extractAt(level,source,s->s.is(item),Math.min(16,needed));
            if(removed.isEmpty()){reason=WorkBlockReason.MATERIALS;return Result.DEFER;}
            boolean full=false;
            for(var stack:removed.stacks()){
                var rest=npc.citizenData().inventory().add(stack);
                if(!rest.isEmpty()){
                    full=true;rest=stores.insertAt(level,source,rest);
                    if(!rest.isEmpty()){var entity=new ItemEntity(level,npc.getX(),npc.getY(),npc.getZ(),rest);level.addFreshEntity(entity);DroppedItemHauling.publishIfNeeded(level,npc.blockPosition());}
                }
            }
            if(full){reason=WorkBlockReason.INVENTORY_FULL;return Result.DEFER;}
            source=null;return Result.RUNNING;
        }
        for(var p:stores.containersWithItem(level,npc.blockPosition(),s->s.is(item),64).stream()
                .filter(pos->CitizenStorageAccess.mayUse(npc,pos)&&npc.citizenData().canTravelTo(pos)).limit(8).toList())
            if(npc.citizenData().canTravelTo(p)&&(near(level,p)||walk(level,p,null,null))){source=p;return Result.RUNNING;}
        reason=WorkBlockReason.MATERIALS;return Result.DEFER;
    }
    private Result scaffolds(ServerLevel level,ConstructionData.Plan plan,BuildingBlueprint blueprint,CottageBlueprint.Placement placement,boolean cleanup){
        var result=cleanup?scaffold.cleanup(plan):scaffold.access(plan,blueprint,placement);reason=scaffold.reason();
        var supply=scaffold.supply();if(supply!=null)return fetch(level,supply.item(),plan,supply.count());
        return result;
    }
    public Result tick(CitizenJob job){
        var level=(ServerLevel)npc.level();working=false;reason=WorkBlockReason.NONE;scaffold.beginTick();
        var plan=ConstructionData.forLevel(level).at(job.target());
        if(!ConstructionService.allowed(npc,job))return Result.DEFER;
        // A previously started scaffold route may extend beyond the blueprint bounds.
        // Defer cleanup and placement alike rather than reading or modifying unloaded blocks.
        if (!ConstructionService.worksiteLoaded(level, plan)) {
            plan.status = "unloaded";
            reason = WorkBlockReason.NO_PATH;
            return Result.DEFER;
        }
        var blueprint=BlueprintCatalog.forPlan(level,plan);
        if(plan.cancelled||plan.cleanup||blueprint==null&&!plan.temporary.isEmpty()){
            var result=scaffolds(level,plan,blueprint,null,true);
            if(result==Result.COMPLETE){
                if(plan.cancelled){ConstructionData.forLevel(level).finishCancel(plan);return Result.COMPLETE;}
                // A missing/changed blueprint may reclaim its own scaffolds, but never resume a different building.
                if(blueprint==null)return Result.DEFER;
            }else return result;
        }
        var pending=ConstructionService.next(level,plan);
        if(pending.isEmpty()){
            if(!plan.temporary.isEmpty()){var result=scaffolds(level,plan,blueprint,null,true);return result==Result.COMPLETE?Result.RUNNING:result;}
            return plan.completed?Result.COMPLETE:Result.DEFER;
        }
        var placement=pending.get();var pos=placement.cells().get(0).at(plan.origin,plan.rotation);
        if(!pos.equals(target)){target=pos;progress=0;source=null;}
        for(var cell:placement.cells()){
            var at=cell.at(plan.origin,plan.rotation);
            if(!level.hasChunkAt(at)||!npc.citizenData().canTravelTo(at)||!level.getWorldBorder().isWithinBounds(at)){plan.status="unloaded";reason=WorkBlockReason.NO_PATH;return Result.DEFER;}
            if((at.getY()==plan.origin.getY()||cell.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    ||cell.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock&&at.getY()==plan.origin.getY()+1)
                    &&!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP)){
                plan.status="foundation";reason=WorkBlockReason.OCCUPIED;return Result.DEFER;
            }
            var actual=level.getBlockState(at);
            if(!CottageBlueprint.same(actual,cell.oriented(plan.rotation))&&(!actual.canBeReplaced()||!actual.getFluidState().isEmpty()||level.getBlockEntity(at)!=null)){
                plan.status="blocked";reason=WorkBlockReason.OCCUPIED;return Result.DEFER;
            }
        }
        if(plan.route!=null&&scaffold.building(plan))return scaffolds(level,plan,blueprint,placement,false);
        if(npc.citizenData().inventory().countPersonalItem(placement.item())<placement.count()){plan.status="materials";return fetch(level,placement.item(),plan);}
        if(!placement.cells().stream().allMatch(c->near(level,c.at(plan.origin,plan.rotation)))||overlaps(level,plan,placement,npc.getBoundingBox())){
            plan.status="travelling";
            if(!npc.commandController().hasActiveCommand()&&!walk(level,pos,plan,placement))return scaffolds(level,plan,blueprint,placement,false);
            return Result.RUNNING;
        }
        for(var cell:placement.cells())if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(cell.at(plan.origin,plan.rotation)),e->e.isAlive()&&!e.isSpectator()).isEmpty()){
            plan.status="occupied";reason=WorkBlockReason.OCCUPIED;return Result.DEFER;
        }
        npc.commandController().stop();npc.setBrainState(CitizenBrainState.WORK);working=true;plan.status="working";
        npc.getLookControl().setLookAt(Vec3.atCenterOf(pos));if(npc.tickCount%10==0)npc.swing(InteractionHand.MAIN_HAND);
        progress+=CitizenSkillRules.workRate(npc.citizenData(),WorkType.BUILDING);if(progress<40)return Result.RUNNING;
        // The board lease and plan validity were checked this tick; commit all furniture cells or restore all snapshots.
        for(var cell:placement.cells()){
            var state=cell.oriented(plan.rotation);var at=cell.at(plan.origin,plan.rotation);
            boolean pairedUpper=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    &&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
            if(!pairedUpper&&!state.canSurvive(level,at)){plan.status="foundation";reason=WorkBlockReason.OCCUPIED;return Result.DEFER;}
        }
        var snapshots=new ArrayList<BlockSnapshot>();boolean placed=true;
        for(var cell:placement.cells()){
            var at=cell.at(plan.origin,plan.rotation);snapshots.add(BlockSnapshot.create(level.dimension(),level,at));
            if(!level.getBlockState(at).equals(cell.oriented(plan.rotation))&&!level.setBlock(at,cell.oriented(plan.rotation),2)){placed=false;break;}
        }
        if(!placed||ForgeEventFactory.onMultiBlockPlace(npc,snapshots,Direction.UP)||npc.citizenData().inventory().countPersonalItem(placement.item())<placement.count()){
            for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(true,false);
            plan.status="protected";reason=WorkBlockReason.PROTECTED;return Result.DEFER;
        }
        npc.citizenData().inventory().removePersonalItem(placement.item(),placement.count());
        for(var cell:placement.cells()){var at=cell.at(plan.origin,plan.rotation);level.updateNeighborsAt(at,cell.state().getBlock());}
        npc.citizenData().practice(CitizenSkill.CONSTRUCTION,5);progress=0;target=null;plan.status="ready";
        ConstructionService.next(level,plan);return plan.completed?Result.COMPLETE:Result.RUNNING;
    }
}
