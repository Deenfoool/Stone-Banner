package dev.stonebanner.production;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.storage.StorageData;
import dev.stonebanner.storage.CitizenStorageAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.*;
import java.util.function.Predicate;

/** Fetching owns no imaginary escrow: interrupted ingredients stay in the persistent personal bag. */
public final class CitizenProductionController {
    public enum Result { RUNNING, COMPLETE, DEFER }
    private final HumanNpcEntity npc;
    private BlockPos source;
    private long billId;
    private double progress;
    private boolean working;
    private WorkBlockReason reason=WorkBlockReason.NONE;
    public CitizenProductionController(HumanNpcEntity npc){this.npc=npc;}
    public WorkBlockReason reason(){return reason;}
    public boolean working(){return working;}
    public void clear(){source=null;billId=0;progress=0;working=false;reason=WorkBlockReason.NONE;}
    private net.minecraft.server.level.ServerPlayer adapter(ServerLevel level){
        var player=FakePlayerFactory.get(level,new GameProfile(npc.getUUID(),"[SBWorker]"));player.setPos(npc.getX(),npc.getY(),npc.getZ());return player;
    }
    private boolean visible(ServerLevel level,BlockPos pos){
        var hit=level.clip(new ClipContext(npc.getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(pos);
    }
    private boolean near(ServerLevel level,BlockPos pos){return npc.distanceToSqr(Vec3.atCenterOf(pos))<=2.75*2.75&&visible(level,pos);}
    private boolean walk(ServerLevel level,BlockPos target){
        for(Direction d:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
            var pos=target.relative(d).offset(0,dy,0);
            if(level.hasChunkAt(pos)&&npc.citizenData().canTravelTo(pos)&&BlockPathfinder.isWalkable(level,pos)
                    &&npc.commandController().issueSystemMove(pos,CitizenBrainState.WORK))return true;
        }
        return false;
    }
    private int slot(Predicate<ItemStack> predicate){var bag=npc.citizenData().inventory().personalSnapshot();for(int i=0;i<bag.size();i++)if(!bag.get(i).isEmpty()&&predicate.test(bag.get(i)))return i;return -1;}
    private Result supplies(ServerLevel level,Predicate<ItemStack> predicate){
        var storage=StorageData.forLevel(level);
        if(source!=null){
            if(!CitizenStorageAccess.mayUse(npc,source)){reason=WorkBlockReason.MATERIALS;return Result.DEFER;}
            if(!level.hasChunkAt(source)||!npc.citizenData().canTravelTo(source)){reason=WorkBlockReason.NO_PATH;return Result.DEFER;}
            if(!near(level,source)){if(!npc.commandController().hasActiveCommand()){reason=WorkBlockReason.NO_PATH;return Result.DEFER;}return Result.RUNNING;}
            npc.commandController().stop();var removed=storage.extractAt(level,source,predicate,1);
            if(removed.isEmpty()){reason=WorkBlockReason.MATERIALS;return Result.DEFER;}
            var stack=removed.stacks().get(0);var rest=npc.citizenData().inventory().add(stack);
            if(!rest.isEmpty()){var leftover=storage.insertAt(level,source,rest);if(!leftover.isEmpty())drop(level,source,leftover,null);reason=WorkBlockReason.INVENTORY_FULL;return Result.DEFER;}
            source=null;return Result.RUNNING;
        }
        for(var pos:storage.containersWithItem(level,npc.blockPosition(),predicate,64).stream()
                .filter(p->CitizenStorageAccess.mayUse(npc,p)&&npc.citizenData().canTravelTo(p)).limit(8).toList()){
            if(!npc.citizenData().canTravelTo(pos))continue;
            if(near(level,pos)||walk(level,pos)){source=pos.immutable();return Result.RUNNING;}
        }
        reason=WorkBlockReason.MATERIALS;return Result.DEFER;
    }
    private boolean work(ServerLevel level,BlockPos target,WorkType type,double ticks){
        if(!near(level,target)){if(!npc.commandController().hasActiveCommand()&&!walk(level,target))reason=WorkBlockReason.NO_PATH;return false;}
        npc.commandController().stop();npc.setBrainState(CitizenBrainState.WORK);npc.getLookControl().setLookAt(Vec3.atCenterOf(target));
        if(npc.tickCount%10==0)npc.swing(InteractionHand.MAIN_HAND);
        working=true;progress+=CitizenSkillRules.workRate(npc.citizenData(),type);return progress>=ticks;
    }
    public Result tick(CitizenJob job){
        var level=(ServerLevel)npc.level();reason=WorkBlockReason.NONE;working=false;
        if(!ProductionService.allowed(npc,job))return Result.DEFER;
        return job.workType()==WorkType.FARMING?farm(level,job):craft(level,job);
    }
    private Result farm(ServerLevel level,CitizenJob job){
        var data=ProductionData.forLevel(level);var field=data.fieldAt(job.target());
        if(!ProductionService.pendingFarm(level,field,job.target()))return Result.DEFER;
        var soil=job.target();var plantPos=soil.above();var ground=level.getBlockState(soil);var plant=level.getBlockState(plantPos);
        if(!ground.is(Blocks.FARMLAND)){
            if(ProductionService.weeds(plant)){
                if(!work(level,plantPos,WorkType.FARMING,20))return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;
                if(ForgeHooks.onBlockBreakEvent(level,net.minecraft.world.level.GameType.SURVIVAL,adapter(level),plantPos)<0){reason=WorkBlockReason.PROTECTED;return Result.DEFER;}
                if(!level.destroyBlock(plantPos,true,npc))return Result.DEFER;
                DroppedItemHauling.publishIfNeeded(level,plantPos);return Result.COMPLETE;
            }
            int hoe=slot(s->s.getItem() instanceof HoeItem);if(hoe<0)return supplies(level,s->s.getItem() instanceof HoeItem);
            if(!work(level,plantPos,WorkType.FARMING,30))return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;
            if(ForgeHooks.onBlockBreakEvent(level,net.minecraft.world.level.GameType.SURVIVAL,adapter(level),soil)<0){reason=WorkBlockReason.PROTECTED;return Result.DEFER;}
            var player=adapter(level);var tool=npc.citizenData().inventory().stack(hoe);player.setItemInHand(InteractionHand.MAIN_HAND,tool);
            var result=tool.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(soil),Direction.UP,soil,false)));
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            if(!result.consumesAction()){reason=WorkBlockReason.PROTECTED;return Result.DEFER;}
            return level.getBlockState(soil).is(Blocks.FARMLAND)?Result.COMPLETE:Result.DEFER;
        }
        if(plant.isAir()){
            int seed=slot(s->s.is(field.crop.seed()));if(seed<0)return supplies(level,s->s.is(field.crop.seed()));
            if(!work(level,plantPos,WorkType.FARMING,20))return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;
            var state=field.crop.block().defaultBlockState();if(!state.canSurvive(level,plantPos))return Result.DEFER;
            var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,plantPos);
            if(!level.setBlock(plantPos,state,3))return Result.DEFER;
            if(net.minecraftforge.event.ForgeEventFactory.onBlockPlace(npc,snapshot,Direction.UP)){snapshot.restore(true,false);reason=WorkBlockReason.PROTECTED;return Result.DEFER;}
            npc.citizenData().inventory().removePersonalSlot(seed,1);return Result.COMPLETE;
        }
        if(!field.crop.block().isMaxAge(plant)){
            int meal=slot(s->s.is(Items.BONE_MEAL));if(meal<0)return supplies(level,s->s.is(Items.BONE_MEAL));
            if(!work(level,plantPos,WorkType.FARMING,20))return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;
            return BoneMealItem.growCrop(npc.citizenData().inventory().stack(meal),level,plantPos)?Result.COMPLETE:Result.DEFER;
        }
        if(!work(level,plantPos,WorkType.FARMING,40))return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;
        if(ForgeHooks.onBlockBreakEvent(level,net.minecraft.world.level.GameType.SURVIVAL,adapter(level),plantPos)<0){reason=WorkBlockReason.PROTECTED;return Result.DEFER;}
        var before=new HashSet<UUID>();for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(plantPos).inflate(2)))before.add(item.getUUID());
        if(!level.destroyBlock(plantPos,true,npc))return Result.DEFER;
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(plantPos).inflate(2),ItemEntity::isAlive)){
            if(before.contains(item.getUUID()))continue;var stack=item.getItem();
            CargoOwnership.markDrop(item, field.owner);
            // Keep exactly one physical planting unit; all remaining harvested items are work cargo.
            if(stack.is(field.crop.seed())&&npc.citizenData().inventory().countPersonalItem(field.crop.seed())==0){var one=stack.copy();one.setCount(1);if(npc.citizenData().inventory().add(one).isEmpty())stack.shrink(1);}
            var rest=npc.citizenData().workPriority(WorkType.HAULING)==WorkPriority.DISABLED?stack:npc.citizenData().inventory().addHaulCargo(stack,field.owner);if(rest.isEmpty())item.discard();else item.setItem(rest);
        }
        DroppedItemHauling.publishIfNeeded(level,plantPos);return Result.COMPLETE;
    }
    private Result craft(ServerLevel level,CitizenJob job){
        var data=ProductionData.forLevel(level);
        if(billId==0){var selected=ProductionService.activeBill(level,job.target());if(selected==null)return Result.DEFER;billId=selected.id;}
        var bill=data.bill(billId);if(bill==null||bill.paused||bill.finished())return Result.DEFER;
        var recipe=ProductionService.recipe(level,bill);
        if(recipe==null||!ProductionService.station(level,bill.station)||!ProductionService.stationAccepts(level,bill.station,recipe))return Result.DEFER;
        var defaultOutput=recipe.getResultItem(level.registryAccess());
        if(bill.mode==ProductionData.Mode.MAINTAIN&&ProductionService.stock(level,bill,defaultOutput)>=bill.amount)return Result.DEFER;
        var matched=CraftingPlan.match(recipe,npc.citizenData().inventory().personalSnapshot(),level);
        if(matched.isEmpty()){
            var missing=CraftingPlan.missing(recipe,npc.citizenData().inventory().personalSnapshot());
            if(missing.isEmpty()){bill.status="recipe";reason=WorkBlockReason.MATERIALS;return Result.DEFER;}
            bill.status="materials";return supplies(level,missing::test);
        }
        if(!work(level,bill.station,WorkType.CRAFTING,80)){bill.status="working";return reason==WorkBlockReason.NO_PATH?Result.DEFER:Result.RUNNING;}
        // Recompute immediately before committing: food consumption, inventory edits and recipe reloads may change it.
        matched=CraftingPlan.match(recipe,npc.citizenData().inventory().personalSnapshot(),level);if(matched.isEmpty())return Result.DEFER;
        var match=matched.get();var output=recipe.assemble(match.grid(),level.registryAccess());if(output.isEmpty())return Result.DEFER;
        if(StorageData.forLevel(level).acceptingContainers(level,bill.station,List.of(output),64).stream()
                .filter(pos->CitizenStorageAccess.mayUse(npc,pos)).limit(8).noneMatch(pos->reachable(level,pos))){bill.status="output";reason=WorkBlockReason.OUTPUT_FULL;return Result.DEFER;}
        var remaining=recipe.getRemainingItems(match.grid());
        for(int slot:match.slots())if(slot>=0)npc.citizenData().inventory().removePersonalSlot(slot,1);
        var player=adapter(level);output.onCraftedBy(level,player,output.getCount());
        net.minecraftforge.event.ForgeEventFactory.firePlayerCraftingEvent(player,output,match.grid());
        bill.made=Math.min(1000000,bill.made+output.getCount());bill.status=bill.finished()?"complete":"ready";data.setDirty();
        storeOutput(level,bill.station,output,bill.owner);for(var stack:remaining)if(!stack.isEmpty())storeOutput(level,bill.station,stack,bill.owner);
        return Result.COMPLETE;
    }
    private void storeOutput(ServerLevel level,BlockPos pos,ItemStack stack,UUID owner){var rest=npc.citizenData().workPriority(WorkType.HAULING)==WorkPriority.DISABLED?stack:npc.citizenData().inventory().addHaulCargo(stack,owner);if(!rest.isEmpty())drop(level,pos,rest,owner);}
    private boolean reachable(ServerLevel level,BlockPos target){
        if(!npc.citizenData().canTravelTo(target))return false;if(near(level,target))return true;
        for(Direction d:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
            var pos=target.relative(d).offset(0,dy,0);
            if(level.hasChunkAt(pos)&&npc.citizenData().canTravelTo(pos)&&BlockPathfinder.isWalkable(level,pos)&&BlockPathfinder.findPath(level,npc.blockPosition(),pos).isPresent())return true;
        }return false;
    }
    private static void drop(ServerLevel level,BlockPos pos,ItemStack stack,UUID owner){var item=new ItemEntity(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,stack.copy());if(owner!=null){item.setThrower(owner);item.getPersistentData().putUUID("SBProductionOwner",owner);}level.addFreshEntity(item);DroppedItemHauling.publishIfNeeded(level,pos.above());}
}
