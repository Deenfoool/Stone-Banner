package dev.stonebanner.construction;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;
import java.util.*;
import static dev.stonebanner.construction.CitizenConstructionController.Result;

/** Physical jump/bridge construction and reverse dismantling. Never teleports or mines permanent blocks. */
public final class ConstructionScaffoldController {
    public record Supply(Item item,int count){}
    private final HumanNpcEntity npc;
    private BlockPos workPos;
    private int jumpTicks;
    private double progress;
    private boolean working;
    private Supply supply;
    private WorkBlockReason reason=WorkBlockReason.NONE;
    public ConstructionScaffoldController(HumanNpcEntity npc){this.npc=npc;}
    public void clear(){workPos=null;progress=0;jumpTicks=0;working=false;supply=null;reason=WorkBlockReason.NONE;}
    public void beginTick(){working=false;supply=null;reason=WorkBlockReason.NONE;}
    public boolean working(){return working;}
    public boolean building(ConstructionData.Plan plan){
        if(plan.route==null)return false;var r=plan.route;
        for(var e:r.blocks().entrySet())if(!loaded(e.getKey())||!plan.temporary.containsKey(e.getKey())&&!reusableBridge(r,e.getKey()))return true;
        return false;
    }
    public WorkBlockReason reason(){return reason;}
    public Supply supply(){return supply;}
    private ServerLevel level(){return (ServerLevel)npc.level();}
    private void dirty(){ConstructionData.forLevel(level()).setDirty();}
    private Result blocked(ConstructionData.Plan plan,String status,WorkBlockReason why){plan.status=status;reason=why;return Result.DEFER;}
    private boolean visible(BlockPos pos,Vec3 eyes){
        for(var p:BlockPos.betweenClosed(BlockPos.containing(Math.min(eyes.x,pos.getX()),Math.min(eyes.y,pos.getY()),Math.min(eyes.z,pos.getZ())),
                BlockPos.containing(Math.max(eyes.x,pos.getX()),Math.max(eyes.y,pos.getY()),Math.max(eyes.z,pos.getZ()))))if(!level().hasChunkAt(p))return false;
        var hit=level().clip(new ClipContext(eyes,Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(pos);
    }
    private boolean near(BlockPos pos){return npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<=3.5*3.5&&visible(pos,npc.getEyePosition());}
    private boolean loaded(BlockPos pos){return level().hasChunkAt(pos)&&level().getWorldBorder().isWithinBounds(pos)&&pos.getY()>=level().getMinBuildHeight()&&pos.getY()<level().getMaxBuildHeight()&&npc.citizenData().canTravelTo(pos);}
    private FakePlayer owner(ConstructionData.Plan plan){return FakePlayerFactory.get(level(),new GameProfile(plan.owner,"StoneBanner"));}
    private boolean permitted(ConstructionData.Plan plan,BlockPos pos){
        return loaded(pos)&&level().mayInteract(owner(plan),pos)&&!ConstructionData.forLevel(level()).reserved(pos,plan)
                &&SettlementData.forLevel(level()).communities().stream().noneMatch(c->c.contains(pos)&&!c.owner().equals(plan.owner));
    }
    private boolean work(ConstructionData.Plan plan,BlockPos pos,String status){
        if(!pos.equals(workPos)){workPos=pos;progress=0;jumpTicks=0;}
        npc.commandController().stop();npc.setBrainState(CitizenBrainState.WORK);working=true;plan.status=status;
        npc.getLookControl().setLookAt(Vec3.atCenterOf(pos));if(npc.tickCount%10==0)npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        progress+=CitizenSkillRules.workRate(npc.citizenData(),WorkType.BUILDING);return progress>=40;
    }
    private boolean centered(BlockPos feet){return Math.abs(npc.getX()-(feet.getX()+.5))<.09&&Math.abs(npc.getZ()-(feet.getZ()+.5))<.09;}
    private void center(BlockPos feet){npc.getMoveControl().setWantedPosition(feet.getX()+.5,npc.getY(),feet.getZ()+.5,.5);}
    private boolean at(BlockPos feet){return loaded(feet)&&loaded(feet.below())&&npc.distanceToSqr(BlockPathfinder.waypoint(level(),feet))<.55*.55;}
    private boolean move(ConstructionData.Plan plan,BlockPos goal){
        if(at(goal))return true;
        if(npc.commandController().hasActiveCommand())return false;
        BlockPos segment=goal;
        if(plan.route!=null&&Math.abs(goal.getY()-npc.getY())>10){
            var r=plan.route;int y=(int)npc.getY()+(goal.getY()>npc.getY()?8:-8);
            y=Math.max(r.base().getY(),Math.min(r.topY(),y));segment=new BlockPos(r.base().getX(),y,r.base().getZ()).relative(r.side());
        }
        if(!loaded(segment)||!BlockPathfinder.isWalkable(level(),segment)
                ||BlockPathfinder.findExactPath(level(),npc.blockPosition(),segment,npc.citizenData()::canTravelTo).isEmpty())return false;
        npc.commandController().issueSystemMove(segment,CitizenBrainState.WORK);return false;
    }
    private Result approach(ConstructionData.Plan plan,BlockPos feet){
        if(move(plan,feet))return Result.RUNNING;
        return npc.commandController().hasActiveCommand()?Result.RUNNING:blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
    }
    public boolean onGroundLevel(ConstructionData.Plan plan){
        return plan.route==null||plan.temporary.isEmpty()||npc.getY()<plan.route.base().getY()+.6
                ||npc.onGround()&&!plan.temporary.containsKey(npc.blockPosition().below())&&!BlockPathfinder.isClimbable(level(),npc.blockPosition());
    }
    /** Descend existing ladders in short segments; the shared pathfinder limits vertical range per query. */
    public Result descend(ConstructionData.Plan plan){
        reason=WorkBlockReason.NONE;supply=null;working=false;plan.status="scaffold_climb";
        if(onGroundLevel(plan))return Result.COMPLETE;
        var r=plan.route;if(r==null)return blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
        if(Math.abs(npc.getX()-(r.base().getX()+r.side().getStepX()+.5))<.6&&Math.abs(npc.getZ()-(r.base().getZ()+r.side().getStepZ()+.5))<.6){
            int y=Math.max(r.base().getY(),(int)Math.floor(npc.getY()-.2)-1);
            return approach(plan,new BlockPos(r.base().getX(),y,r.base().getZ()).relative(r.side()));
        }
        if(Math.abs(npc.getX()-(r.base().getX()+.5))<.55&&Math.abs(npc.getZ()-(r.base().getZ()+.5))<.55){
            int y=Math.max(r.base().getY(),Math.min(r.topY(),(int)Math.floor(npc.getY())-1));
            return approach(plan,new BlockPos(r.base().getX(),y,r.base().getZ()).relative(r.side()));
        }
        return retreat(plan,r.columnTop().above());
    }
    private Result retreat(ConstructionData.Plan plan,BlockPos destination){
        var r=plan.route;if(r==null)return approach(plan,destination);
        int index=-1;double distance=Double.MAX_VALUE;
        for(int i=0;i<r.bridge().size();i++){var p=r.bridge().get(i).above();double d=npc.distanceToSqr(Vec3.atCenterOf(p));if(d<distance){distance=d;index=i;}}
        if(index>=0&&distance<2&&npc.getY()>r.topY()+.5){
            var previous=index==0?r.columnTop():r.bridge().get(index-1);
            if(!previous.above().equals(destination)&&!at(destination))return approach(plan,previous.above());
        }
        return approach(plan,destination);
    }
    private void reconcile(ConstructionData.Plan plan){
        boolean changed=false;var iterator=plan.temporary.entrySet().iterator();
        while(iterator.hasNext()){var e=iterator.next();if(level().hasChunkAt(e.getKey())&&!level().getBlockState(e.getKey()).equals(e.getValue())){iterator.remove();changed=true;}}
        if(changed){plan.cleanup=true;dirty();}
    }
    public Result access(ConstructionData.Plan plan,BuildingBlueprint blueprint,CottageBlueprint.Placement placement){
        reason=WorkBlockReason.NONE;supply=null;working=false;reconcile(plan);
        if(plan.cleanup)return cleanup(plan);
        if(plan.route==null){
            var route=planRoute(plan,blueprint,placement);
            if(route==null)return blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
            plan.route=route;dirty();
        }
        var r=plan.route;
        if(r.blocks().keySet().stream().anyMatch(p->!loaded(p)||!loaded(p.above(2))))return blocked(plan,"unloaded",WorkBlockReason.NO_PATH);
        for(var e:r.blocks().entrySet())if(!plan.temporary.containsKey(e.getKey())&&!reusableBridge(r,e.getKey())&&!level().getBlockState(e.getKey()).isAir()){
            plan.cleanup=true;dirty();return cleanup(plan);
        }
        if(!usableWorkPosition(plan,placement,r.end().above())){plan.cleanup=true;dirty();return cleanup(plan);}
        var needed=new LinkedHashMap<Item,Integer>();
        for(var e:r.blocks().entrySet())if(!plan.temporary.containsKey(e.getKey())&&!reusableBridge(r,e.getKey()))needed.merge(e.getValue().getBlock().asItem(),1,Integer::sum);
        for(var e:needed.entrySet())if(npc.citizenData().inventory().countPersonalItem(e.getKey())<e.getValue()){
            if(!onGroundLevel(plan))return descend(plan);
            supply=new Supply(e.getKey(),e.getValue());return blocked(plan,"scaffold_materials",WorkBlockReason.MATERIALS);
        }
        for(int y=r.base().getY();y<=r.topY();y++){
            var column=new BlockPos(r.base().getX(),y,r.base().getZ());var ladder=column.relative(r.side());
            if(!plan.temporary.containsKey(column))return rise(plan,column);
            if(!plan.temporary.containsKey(ladder)){
                if(!at(column.above()))return approach(plan,column.above());
                return place(plan,ladder,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,r.side()),"scaffold_climb");
            }
        }
        var previous=r.columnTop();
        for(var p:r.bridge()){
            if(!plan.temporary.containsKey(p)&&!reusableBridge(r,p)){
                if(!at(previous.above()))return approach(plan,previous.above());
                if(!centered(previous.above())){npc.commandController().stop();center(previous.above());return Result.RUNNING;}
                return place(plan,p,Blocks.COBBLESTONE.defaultBlockState(),"scaffold_bridge");
            }previous=p;
        }
        plan.status="scaffold_climb";
        if(at(r.end().above())&&!centered(r.end().above())){npc.commandController().stop();center(r.end().above());return Result.RUNNING;}
        return approach(plan,r.end().above());
    }
    private boolean reusableBridge(ScaffoldRoute route,BlockPos pos){return loaded(pos)&&loaded(pos.above(2))&&route.bridge().contains(pos)&&level().getBlockState(pos).isFaceSturdy(level(),pos,Direction.UP)&&BlockPathfinder.isWalkable(level(),pos.above());}
    private Result rise(ConstructionData.Plan plan,BlockPos pos){
        if(!permitted(plan,pos)||!loaded(pos.above(2))||!level().getBlockState(pos).isAir()||!level().getBlockState(pos).getFluidState().isEmpty()
                ||!level().getBlockState(pos.below()).isFaceSturdy(level(),pos.below(),Direction.UP))return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
        if(Math.abs(npc.getY()-pos.getY())>.18&&jumpTicks==0)return approach(plan,pos);
        if(!centered(pos)){center(pos);plan.status="scaffold_climb";return Result.RUNNING;}
        if(jumpTicks==0&&!level().noCollision(npc,npc.getBoundingBox().move(0,1,0)))return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
        if(!work(plan,pos,"scaffold_climb"))return Result.RUNNING;
        if(npc.onGround()&&jumpTicks==0){npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);npc.getJumpControl().jump();jumpTicks=1;return Result.RUNNING;}
        if(jumpTicks>0&&++jumpTicks>80)return blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
        // Set the former feet cell only after the entire body clears its top during a real jump.
        if(npc.getBoundingBox().minY<pos.getY()+1.02)return Result.RUNNING;
        return commitCells(plan,List.of(new CottageBlueprint.Cell(pos,Blocks.COBBLESTONE.defaultBlockState()),
                new CottageBlueprint.Cell(pos.relative(plan.route.side()),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,plan.route.side()))));
    }
    private Result place(ConstructionData.Plan plan,BlockPos pos,BlockState state,String status){
        if(!near(pos)||!permitted(plan,pos)||!state.canSurvive(level(),pos))return blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
        if(!work(plan,pos,status))return Result.RUNNING;return commitPlace(plan,pos,state);
    }
    private Result commitPlace(ConstructionData.Plan plan,BlockPos pos,BlockState state){return commitCells(plan,List.of(new CottageBlueprint.Cell(pos,state)));}
    private Result commitCells(ConstructionData.Plan plan,List<CottageBlueprint.Cell> cells){
        var costs=new LinkedHashMap<Item,Integer>();for(var cell:cells)costs.merge(cell.state().getBlock().asItem(),1,Integer::sum);
        for(var e:costs.entrySet())if(npc.citizenData().inventory().countPersonalItem(e.getKey())<e.getValue())return blocked(plan,"scaffold_materials",WorkBlockReason.MATERIALS);
        for(var cell:cells){var pos=cell.offset();
            if(!permitted(plan,pos)||!level().getBlockState(pos).isAir()||level().getBlockEntity(pos)!=null
                    ||!level().getEntitiesOfClass(LivingEntity.class,new AABB(pos),e->e.isAlive()&&!e.isSpectator()).isEmpty())return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
        }
        var snapshots=new ArrayList<BlockSnapshot>();boolean placed=true;
        for(var cell:cells){var pos=cell.offset();snapshots.add(BlockSnapshot.create(level().dimension(),level(),pos));
            if(!cell.state().canSurvive(level(),pos)||!level().setBlock(pos,cell.state(),2)){placed=false;break;}
        }
        boolean cancelled=placed&&ForgeEventFactory.onMultiBlockPlace(owner(plan),snapshots,Direction.UP);
        if(!placed||cancelled||cells.stream().anyMatch(c->!level().getBlockState(c.offset()).equals(c.state()))
                ||costs.entrySet().stream().anyMatch(e->npc.citizenData().inventory().countPersonalItem(e.getKey())<e.getValue())){
            for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(true,false);
            return blocked(plan,"scaffold_protected",WorkBlockReason.PROTECTED);
        }
        for(var e:costs.entrySet())npc.citizenData().inventory().removePersonalItem(e.getKey(),e.getValue());
        for(var c:cells)plan.temporary.put(c.offset().immutable(),c.state());dirty();
        for(var c:cells)level().updateNeighborsAt(c.offset(),c.state().getBlock());
        progress=0;jumpTicks=0;workPos=null;return Result.RUNNING;
    }
    public Result cleanup(ConstructionData.Plan plan){
        reason=WorkBlockReason.NONE;supply=null;working=false;plan.status="scaffold_cleanup";reconcile(plan);
        if(plan.temporary.isEmpty()){plan.route=null;plan.cleanup=false;dirty();return Result.COMPLETE;}
        if(plan.temporary.keySet().stream().anyMatch(p->!loaded(p)))return blocked(plan,"unloaded",WorkBlockReason.NO_PATH);
        // Wait for each one-block descent to land; do not start navigation in mid-fall.
        if(!npc.onGround()&&!BlockPathfinder.isClimbable(level(),npc.blockPosition()))return Result.RUNNING;
        var r=plan.route;
        if(r!=null){
            for(int i=r.bridge().size()-1;i>=0;i--){
                var p=r.bridge().get(i);if(!plan.temporary.containsKey(p))continue;
                var previous=i==0?r.columnTop():r.bridge().get(i-1);var safeFeet=previous.above();
                if(!at(safeFeet))return retreat(plan,safeFeet);
                if(!centered(safeFeet)){npc.commandController().stop();center(safeFeet);return Result.RUNNING;}
                return remove(plan,p,false);
            }
            for(int y=r.topY();y>=r.base().getY();y--){
                var column=new BlockPos(r.base().getX(),y,r.base().getZ());var ladder=column.relative(r.side());
                if(!plan.temporary.containsKey(column))continue;
                if(!at(column.above()))return approach(plan,column.above());
                if(!npc.onGround())return Result.RUNNING;
                if(!centered(column)){center(column);return Result.RUNNING;}
                if(!safeLanding(column))return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
                if(plan.temporary.containsKey(ladder))return remove(plan,ladder,false);
                return remove(plan,column,true);
            }
        }
        // Damaged/old saves may contain orphaned blocks. Reclaim only reachable, unoccupied cells.
        var remaining=plan.temporary.keySet().stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()).toList();
        for(var p:remaining)if(near(p)&&!new AABB(p).move(0,1,0).intersects(npc.getBoundingBox()))return remove(plan,p,false);
        return blocked(plan,"scaffold_blocked",WorkBlockReason.NO_PATH);
    }
    private boolean safeLanding(BlockPos column){
        var below=column.below();if(!loaded(below)||!level().getBlockState(below).isFaceSturdy(level(),below,Direction.UP)||!level().getFluidState(column).isEmpty())return false;
        var landing=npc.getBoundingBox().move(0,-1,0);
        if(!level().getEntitiesOfClass(LivingEntity.class,landing,e->e!=npc&&e.isAlive()&&!e.isSpectator()).isEmpty())return false;
        for(var p:BlockPos.betweenClosed(BlockPos.containing(landing.minX,landing.minY,landing.minZ),BlockPos.containing(landing.maxX,landing.maxY,landing.maxZ))){
            if(p.equals(column))continue;if(!loaded(p))return false;
            for(var shape:level().getBlockState(p).getCollisionShape(level(),p).toAabbs())if(shape.move(p).intersects(landing))return false;
        }return true;
    }
    private boolean foreignAttachment(ConstructionData.Plan plan,BlockPos pos){
        for(var direction:Direction.values()){
            var p=pos.relative(direction);if(!loaded(p))return true;var s=level().getBlockState(p);
            if(s.isAir()||plan.temporary.containsKey(p))continue;
            if(direction==Direction.UP||s.getBlock() instanceof VineBlock||s.getBlock() instanceof MultifaceBlock||s.getBlock() instanceof TripWireHookBlock)return true;
            if(s.getBlock() instanceof LadderBlock&&s.getValue(LadderBlock.FACING)==direction)return true;
            if((s.getBlock() instanceof WallTorchBlock||s.getBlock() instanceof WallSignBlock||s.getBlock() instanceof WallHangingSignBlock
                    ||s.getBlock() instanceof FaceAttachedHorizontalDirectionalBlock)&&s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                    &&s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)==direction)return true;
        }return false;
    }
    private Result remove(ConstructionData.Plan plan,BlockPos pos,boolean descent){
        var state=plan.temporary.get(pos);if(state==null)return Result.RUNNING;
        if(!permitted(plan,pos)||!near(pos)||foreignAttachment(plan,pos)||level().getBlockEntity(pos)!=null)return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
        var occupied=new AABB(pos).expandTowards(0,2,0);
        if(!level().getEntitiesOfClass(LivingEntity.class,occupied,e->e.isAlive()&&!e.isSpectator()&&(!descent||e!=npc)).isEmpty())return blocked(plan,"scaffold_blocked",WorkBlockReason.OCCUPIED);
        if(descent&&(!npc.onGround()||!centered(pos)||!safeLanding(pos)))return Result.RUNNING;
        if(!work(plan,pos,"scaffold_cleanup"))return Result.RUNNING;
        var event=new BlockEvent.BreakEvent(level(),pos,state,owner(plan));
        boolean cancelled;
        ScaffoldOwnershipEvents.begin();try{cancelled=MinecraftForge.EVENT_BUS.post(event);}finally{ScaffoldOwnershipEvents.end();}
        if(cancelled)return blocked(plan,"scaffold_protected",WorkBlockReason.PROTECTED);
        if(!level().getBlockState(pos).equals(state)||level().getBlockEntity(pos)!=null){plan.temporary.remove(pos);dirty();return Result.RUNNING;}
        if(!level().setBlock(pos,Blocks.AIR.defaultBlockState(),3))return blocked(plan,"scaffold_protected",WorkBlockReason.PROTECTED);
        plan.temporary.remove(pos);dirty();
        // Return the single paid block directly; destroyBlock/drop tables would also spawn it and duplicate supplies.
        var rest=npc.citizenData().inventory().add(new ItemStack(state.getBlock().asItem()));
        if(!rest.isEmpty()){level().addFreshEntity(new ItemEntity(level(),npc.getX(),npc.getY(),npc.getZ(),rest));DroppedItemHauling.publishIfNeeded(level(),npc.blockPosition());}
        if(descent)npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);
        workPos=null;progress=0;return Result.RUNNING;
    }
    private boolean usableWorkPosition(ConstructionData.Plan plan,CottageBlueprint.Placement placement,BlockPos feet){
        if(!loaded(feet)||!loaded(feet.above()))return false;
        var eyes=Vec3.atBottomCenterOf(feet).add(0,npc.getEyeHeight(),0);var body=npc.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(npc.position()));
        for(var cell:placement.cells()){
            var p=cell.at(plan.origin,plan.rotation);
            if(eyes.distanceToSqr(Vec3.atCenterOf(p))>3.5*3.5||!visible(p,eyes))return false;
            for(var box:cell.oriented(plan.rotation).getCollisionShape(level(),p).toAabbs())if(box.move(p).intersects(body))return false;
        }return true;
    }
    private ScaffoldRoute planRoute(ConstructionData.Plan plan,BuildingBlueprint blueprint,CottageBlueprint.Placement placement){
        if(blueprint==null)return null;
        var occupied=new HashSet<BlockPos>();for(var p:blueprint.placements())for(var c:p.cells())occupied.add(c.at(plan.origin,plan.rotation));
        for(var p:blueprint.preserved())occupied.add(plan.origin.offset(p.rotate(plan.rotation)));
        var target=placement.cells().get(0).at(plan.origin,plan.rotation);int top=target.getY();var bounds=plan.bounds().inflate(4);
        var candidates=new ArrayList<BlockPos>();
        for(int x=(int)bounds.minX;x<(int)bounds.maxX;x++)for(int z=(int)bounds.minZ;z<(int)bounds.maxZ;z++){
            if(x>bounds.minX+1&&x<bounds.maxX-2&&z>bounds.minZ+1&&z<bounds.maxZ-2)continue;
            for(int y=Math.max(plan.origin.getY()-4,npc.blockPosition().getY()-2);y<=Math.min(top,npc.blockPosition().getY()+2);y++){
                var p=new BlockPos(x,y,z);if(top-y>=ScaffoldRoute.MAX_HEIGHT||!loaded(p)||occupied.contains(p)||!BlockPathfinder.isWalkable(level(),p)||!level().getBlockState(p).isAir()
                        ||!level().getBlockState(p.below()).isFaceSturdy(level(),p.below(),Direction.UP))continue;candidates.add(p);
            }
        }
        candidates.sort(Comparator.comparingDouble(p->npc.distanceToSqr(Vec3.atCenterOf(p))+.2*p.distManhattan(target)));
        for(var base:candidates.stream().limit(24).toList())for(var side:Direction.Plane.HORIZONTAL){
            boolean clear=true;
            for(int y=base.getY();y<=top;y++){
                var p=new BlockPos(base.getX(),y,base.getZ());
                if(!emptyFuture(plan,p,occupied)||!emptyFuture(plan,p.relative(side),occupied)||!loaded(p.above(2))||!level().getBlockState(p.above()).getCollisionShape(level(),p.above()).isEmpty()
                        ||!level().getBlockState(p.above(2)).getCollisionShape(level(),p.above(2)).isEmpty()){clear=false;break;}
            }
            if(!clear||!BlockPathfinder.isWalkable(level(),base.relative(side))||BlockPathfinder.findExactPath(level(),npc.blockPosition(),base,npc.citizenData()::canTravelTo).isEmpty())continue;
            var start=new BlockPos(base.getX(),top,base.getZ());var road=bridgePath(plan,placement,start,start.relative(side),occupied,bounds);
            if(road!=null)try{return new ScaffoldRoute(base,top,side,road);}catch(IllegalArgumentException ignored){}
        }return null;
    }
    private boolean emptyFuture(ConstructionData.Plan plan,BlockPos pos,Set<BlockPos> occupied){return !occupied.contains(pos)&&permitted(plan,pos)&&level().getBlockState(pos).isAir()&&level().getBlockEntity(pos)==null&&level().getFluidState(pos).isEmpty();}
    private List<BlockPos> bridgePath(ConstructionData.Plan plan,CottageBlueprint.Placement placement,BlockPos start,BlockPos ladder,Set<BlockPos> occupied,AABB bounds){
        var queue=new ArrayDeque<BlockPos>();var parents=new HashMap<BlockPos,BlockPos>();queue.add(start);parents.put(start,start);
        while(!queue.isEmpty()&&parents.size()<=4096){
            var p=queue.removeFirst();
            if(usableWorkPosition(plan,placement,p.above())){
                var path=new ArrayList<BlockPos>();while(!p.equals(start)){path.add(p);p=parents.get(p);}Collections.reverse(path);return path.size()<=ScaffoldRoute.MAX_BRIDGE?path:null;
            }
            for(var direction:Direction.Plane.HORIZONTAL){
                var next=p.relative(direction);if(parents.containsKey(next)||next.equals(ladder)||!bounds.contains(Vec3.atCenterOf(next))||!loaded(next.above(2)))continue;
                if(!emptyFuture(plan,next,occupied)||occupied.contains(next.above())||occupied.contains(next.above(2)))continue;
                if(!level().getBlockState(next.above()).getCollisionShape(level(),next.above()).isEmpty()||!level().getBlockState(next.above(2)).getCollisionShape(level(),next.above(2)).isEmpty())continue;
                parents.put(next,p);queue.addLast(next);
            }
        }return null;
    }
}
