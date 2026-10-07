package dev.stonebanner.production;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class ProductionService {
    private ProductionService(){}
    public static CraftingRecipe recipe(ServerLevel level,ProductionData.Bill bill){
        var recipe=level.getRecipeManager().byKey(bill.recipe).orElse(null);
        return recipe instanceof CraftingRecipe crafting&&CraftingPlan.supported(crafting)?crafting:null;
    }
    public static boolean station(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&(level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)||level.getBlockState(pos).is(ProductionBlocks.CARPENTER.get())||level.getBlockState(pos).is(ProductionBlocks.FORGE.get()));}
    public static boolean stationAccepts(ServerLevel level,BlockPos pos,CraftingRecipe recipe){
        if(level.getBlockState(pos).is(Blocks.CRAFTING_TABLE))return true;
        var category=recipe.category();
        if(level.getBlockState(pos).is(ProductionBlocks.CARPENTER.get()))return category==net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING||category==net.minecraft.world.item.crafting.CraftingBookCategory.REDSTONE||category==net.minecraft.world.item.crafting.CraftingBookCategory.MISC;
        return category==net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT||category==net.minecraft.world.item.crafting.CraftingBookCategory.REDSTONE||category==net.minecraft.world.item.crafting.CraftingBookCategory.MISC;
    }
    public static int stock(ServerLevel level,ProductionData.Bill bill,ItemStack output){
        int count=0;var storage=StorageData.forLevel(level);
        for(var pos:storage.registeredPositions())if(level.hasChunkAt(pos)&&pos.distSqr(bill.station)<=64*64&&level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)
            for(int i=0;i<container.getContainerSize();i++){var stack=container.getItem(i);if(ItemStack.isSameItemSameTags(output,stack))count+=stack.getCount();}
        for(var npc:level.getEntitiesOfClass(HumanNpcEntity.class,new AABB(bill.station).inflate(64),HumanNpcEntity::isAlive))
            for(var cargo:npc.citizenData().inventory().haulCargoSnapshot())if(ItemStack.isSameItemSameTags(output,cargo.stack()))count+=cargo.stack().getCount();
        for(var dropped:level.getEntitiesOfClass(ItemEntity.class,new AABB(bill.station).inflate(64),e->e.isAlive()&&e.getPersistentData().hasUUID("SBProductionOwner")&&bill.owner.equals(e.getPersistentData().getUUID("SBProductionOwner"))))
            if(ItemStack.isSameItemSameTags(output,dropped.getItem()))count+=dropped.getItem().getCount();
        return count;
    }
    public static ProductionData.Bill activeBill(ServerLevel level,BlockPos station){
        var data=ProductionData.forLevel(level);
        for(var bill:data.bills()){
            if(!bill.station.equals(station)||bill.paused)continue;
            if(bill.finished()){bill.status="complete";continue;}
            var recipe=recipe(level,bill);if(recipe==null){bill.status="recipe";continue;}
            if(!station(level,station)||!stationAccepts(level,station,recipe)){bill.status="station";continue;}
            if(bill.mode==ProductionData.Mode.MAINTAIN&&stock(level,bill,recipe.getResultItem(level.registryAccess()))>=bill.amount){bill.status="satisfied";continue;}
            return bill;
        }
        return null;
    }
    public static boolean pendingFarm(ServerLevel level,ProductionData.Field field,BlockPos soil){
        if(field==null||field.paused||!level.hasChunkAt(soil)||!field.contains(soil))return false;
        var ground=level.getBlockState(soil);var plant=level.getBlockState(soil.above());
        if(ground.is(Blocks.DIRT)||ground.is(Blocks.GRASS_BLOCK)||ground.is(Blocks.DIRT_PATH))return plant.isAir()||weeds(plant);
        if(!ground.is(Blocks.FARMLAND))return false;
        if(plant.isAir())return field.crop.block().defaultBlockState().canSurvive(level,soil.above());
        return plant.is(field.crop.block())&&(field.crop.block().isMaxAge(plant)||field.fertilize);
    }
    public static boolean valid(ServerLevel level,CitizenJob job){
        return job.workType()==WorkType.FARMING?pendingFarm(level,ProductionData.forLevel(level).fieldAt(job.target()),job.target()):activeBill(level,job.target())!=null;
    }
    public static boolean allowed(HumanNpcEntity npc,CitizenJob job){
        var level=(ServerLevel)npc.level();var field=ProductionData.forLevel(level).fieldAt(job.target());var bill=job.workType()==WorkType.CRAFTING?activeBill(level,job.target()):null;
        UUID owner=job.workType()==WorkType.FARMING?(field==null?null:field.owner):(bill==null?null:bill.owner);
        return owner!=null&&npc.citizenData().recruitedBy().map(owner::equals).orElse(true);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level)||level.getGameTime()%100!=0)return;
        reconcile(level);
    }
    public static void reconcile(ServerLevel level){
        var data=ProductionData.forLevel(level);var board=CitizenJobBoard.forLevel(level);
        for(var job:board.snapshot())if((job.workType()==WorkType.FARMING||job.workType()==WorkType.CRAFTING)&&!valid(level,job))board.remove(job.id());
        int budget=4096;var fields=data.fields().stream().filter(f->!f.paused).toList();
        int processed=0;
        while(processed<fields.size()&&budget>0){
            var field=fields.get((data.fieldCursor+processed++)%fields.size());
            for(var soil:BlockPos.betweenClosed(field.min,field.max)){
                if(--budget<0)break;
                if(pendingFarm(level,field,soil))board.publish(WorkType.FARMING,soil,level.getGameTime());
            }
        }
        if(!fields.isEmpty())data.fieldCursor=(data.fieldCursor+processed)%fields.size();
        Set<BlockPos> stations=new HashSet<>();for(var bill:data.bills())stations.add(bill.station);
        for(var station:stations)if(activeBill(level,station)!=null)board.publish(WorkType.CRAFTING,station,level.getGameTime());
    }
    static boolean weeds(net.minecraft.world.level.block.state.BlockState state){return state.is(Blocks.GRASS)||state.is(Blocks.TALL_GRASS)||state.is(Blocks.FERN)||state.is(Blocks.LARGE_FERN)||state.is(Blocks.DEAD_BUSH);}
}
