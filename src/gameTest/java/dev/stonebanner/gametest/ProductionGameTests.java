package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.production.*;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class ProductionGameTests {
    private static final BlockPos SOIL=new BlockPos(4,0,2),TABLE=new BlockPos(4,1,2),CHEST=new BlockPos(2,1,4);
    private static HumanNpcEntity prepare(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);
        // Keep production fixtures isolated without deleting plans belonging to other test arenas.
        var data=ProductionData.forLevel(h.getLevel());
        var arena=new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(16,6,16)));
        for(var f:data.fields())if(arena.contains(net.minecraft.world.phys.Vec3.atCenterOf(f.min)))data.edit(f.owner,f.id,"remove");
        for(var b:data.bills())if(arena.contains(net.minecraft.world.phys.Vec3.atCenterOf(b.station)))data.edit(b.owner,b.id,"remove");
        for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        h.setBlock(new BlockPos(6,2,2),Blocks.GLOWSTONE);
        return npc;
    }
    private static Container storage(GameTestHelper h){
        h.setBlock(CHEST,Blocks.CHEST);StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(CHEST));
        return (Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));
    }
    private static ProductionData.Bill bill(GameTestHelper h,String recipe,ProductionData.Mode mode,int amount){
        h.setBlock(TABLE,Blocks.CRAFTING_TABLE);var d=ProductionData.forLevel(h.getLevel());
        return d.bill(d.addBill(UUID.randomUUID(),h.absolutePos(TABLE),new ResourceLocation("minecraft",recipe),mode,amount));
    }
    private static CitizenJob job(GameTestHelper h,WorkType type,BlockPos target){return CitizenJob.simple(1,type,h.absolutePos(target),h.getLevel().getGameTime());}
    private static CitizenProductionController.Result finish(GameTestHelper h,CitizenProductionController controller,CitizenJob job){
        var result=CitizenProductionController.Result.RUNNING;
        for(int i=0;i<400&&result==CitizenProductionController.Result.RUNNING;i++)result=controller.tick(job);
        return result;
    }
    private static int count(Container c,Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}

    @GameTest(template="empty",timeoutTicks=700,batch="production-cycle")
    public static void farmerFetchesHoePlantsHarvestsDeliversAndReplants(GameTestHelper h){
        var npc=prepare(h);var chest=storage(h);chest.setItem(0,new ItemStack(Items.IRON_HOE));chest.setItem(1,new ItemStack(Items.WHEAT_SEEDS,8));
        h.setBlock(SOIL,Blocks.DIRT);h.setBlock(new BlockPos(4,0,3),Blocks.WATER);h.setBlock(SOIL.above(),Blocks.AIR);
        npc.setWorkPriority(WorkType.FARMING,WorkPriority.NORMAL);npc.setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);
        var data=ProductionData.forLevel(h.getLevel());long field=data.addField(UUID.randomUUID(),h.absolutePos(SOIL),h.absolutePos(SOIL),FarmCrop.WHEAT);
        h.assertTrue(field>0,"Field registration failed");
        ProductionService.reconcile(h.getLevel());
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getBlockState(SOIL.above()).is(Blocks.WHEAT),"Farmer did not fetch/till/plant: soil="+h.getBlockState(SOIL)+" plant="+h.getBlockState(SOIL.above())+" bag="+npc.citizenData().inventory().personalSnapshot()+" brain="+npc.brainState()+" blocked="+npc.workController().blockReason()+" job="+npc.workController().currentJob()))
            .thenExecute(()->{h.assertTrue(npc.citizenData().inventory().personalSnapshot().stream().anyMatch(s->s.is(Items.IRON_HOE)&&s.getDamageValue()>0),"Hoe durability was not used");h.setBlock(SOIL.above(),FarmCrop.WHEAT.block().getStateForAge(7));})
            .thenWaitUntil(()->h.assertTrue(count(chest,Items.WHEAT)>0,"Harvest was not delivered"))
            .thenWaitUntil(()->h.assertTrue(h.getBlockState(SOIL.above()).is(Blocks.WHEAT)&&!FarmCrop.WHEAT.block().isMaxAge(h.getBlockState(SOIL.above())),"Farmer did not replant"))
            .thenExecute(()->{data.edit(data.field(field).owner,field,"pause");npc.setWorkPriority(WorkType.FARMING,WorkPriority.DISABLED);h.assertTrue(npc.citizenData().experience(CitizenSkill.AGRICULTURE)>0,"Farming did not award practice");}).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=500,batch="production-craft")
    public static void crafterFetchesRealIngredientsAndDeliversExactlyOneBatch(GameTestHelper h){
        var npc=prepare(h);var chest=storage(h);chest.setItem(0,new ItemStack(Items.OAK_PLANKS,2));var bill=bill(h,"stick",ProductionData.Mode.MAKE,4);
        npc.setWorkPriority(WorkType.CRAFTING,WorkPriority.NORMAL);npc.setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);ProductionService.reconcile(h.getLevel());
        h.startSequence().thenWaitUntil(()->h.assertTrue(count(chest,Items.STICK)==4,"Crafted sticks were not delivered"))
            .thenExecuteAfter(40,()->{h.assertTrue(bill.made==4&&bill.finished(),"MAKE was not completed once");h.assertTrue(count(chest,Items.OAK_PLANKS)==0,"Ingredients not consumed");h.assertTrue(npc.citizenData().experience(CitizenSkill.CRAFTING)>0,"Crafting did not award practice");}).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void allFourCropsPlantConsumeRealSeedsAndIgnoreImmaturePlants(GameTestHelper h){
        var npc=prepare(h);var d=ProductionData.forLevel(h.getLevel());
        h.startSequence().thenExecuteAfter(10,()->{
        for(var crop:FarmCrop.values()){
            h.setBlock(SOIL,Blocks.FARMLAND);h.setBlock(SOIL.above(),Blocks.AIR);long id=d.addField(UUID.randomUUID(),h.absolutePos(SOIL),h.absolutePos(SOIL),crop);
            npc.citizenData().inventory().add(new ItemStack(crop.seed(),2));var controller=new CitizenProductionController(npc);
            var result=finish(h,controller,job(h,WorkType.FARMING,SOIL));h.assertTrue(result==CitizenProductionController.Result.COMPLETE,"Plant failed: "+crop+" result="+result+" reason="+controller.reason()+" id="+id+" soil="+h.getBlockState(SOIL)+" brightness="+h.getLevel().getRawBrightness(h.absolutePos(SOIL.above()),0)+" sky="+h.getLevel().canSeeSky(h.absolutePos(SOIL.above())));
            h.assertTrue(h.getBlockState(SOIL.above()).is(crop.block()),"Wrong crop");h.assertTrue(npc.citizenData().inventory().countPersonalItem(crop.seed())==1,"Seed was not consumed once");
            h.assertTrue(!ProductionService.pendingFarm(h.getLevel(),d.field(id),h.absolutePos(SOIL)),"Immature crop scheduled for harvest");d.edit(d.field(id).owner,id,"remove");
        }
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void missingSeedsDoNotCreatePlants(GameTestHelper h){
        var npc=prepare(h);h.setBlock(SOIL,Blocks.FARMLAND);var d=ProductionData.forLevel(h.getLevel());long id=d.addField(UUID.randomUUID(),h.absolutePos(SOIL),h.absolutePos(SOIL),FarmCrop.WHEAT);
        h.startSequence().thenExecuteAfter(10,()->{
        var controller=new CitizenProductionController(npc);h.assertTrue(controller.tick(job(h,WorkType.FARMING,SOIL))==CitizenProductionController.Result.DEFER,"Missing seeds not deferred");
        h.assertTrue(controller.reason()==WorkBlockReason.MATERIALS&&h.getBlockState(SOIL.above()).isAir(),"Free planting or missing reason: "+controller.reason()+" soil="+h.getBlockState(SOIL)+" plant="+h.getBlockState(SOIL.above())+" id="+id);d.edit(d.field(id).owner,id,"pause");}).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void fullStoragePreventsIngredientConsumption(GameTestHelper h){
        var npc=prepare(h);var chest=storage(h);for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        var b=bill(h,"bread",ProductionData.Mode.MAKE,1);npc.citizenData().inventory().add(new ItemStack(Items.WHEAT,3));var controller=new CitizenProductionController(npc);
        h.assertTrue(finish(h,controller,job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.DEFER,"Full output not deferred");
        h.assertTrue(controller.reason()==WorkBlockReason.OUTPUT_FULL&&b.made==0,"Wrong output state");h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.WHEAT)==3,"Blocked craft consumed ingredients");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void maintainIncludesCargoAndResumesWhenStockRemoved(GameTestHelper h){
        var npc=prepare(h);npc.setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);storage(h);var b=bill(h,"bread",ProductionData.Mode.MAINTAIN,1);
        npc.citizenData().inventory().add(new ItemStack(Items.WHEAT,6));var controller=new CitizenProductionController(npc);
        h.assertTrue(finish(h,controller,job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.COMPLETE,"First batch failed");
        h.assertTrue(ProductionService.stock(h.getLevel(),b,new ItemStack(Items.BREAD))==1&&ProductionService.activeBill(h.getLevel(),b.station)==null,"Cargo not counted, repeated craft possible");
        npc.citizenData().inventory().clear();h.assertTrue(ProductionService.activeBill(h.getLevel(),b.station)==b,"Maintain did not resume after consumption");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void cakeReturnsBucketsAndCountsActualOutput(GameTestHelper h){
        var npc=prepare(h);npc.setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);storage(h);var b=bill(h,"cake",ProductionData.Mode.MAKE,1);
        for(var stack:List.of(new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.SUGAR,2),new ItemStack(Items.EGG),new ItemStack(Items.WHEAT,3)))npc.citizenData().inventory().add(stack);
        h.assertTrue(finish(h,new CitizenProductionController(npc),job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.COMPLETE,"Cake recipe failed");
        int buckets=npc.citizenData().inventory().haulCargoSnapshot().stream().filter(c->c.stack().is(Items.BUCKET)).mapToInt(c->c.stack().getCount()).sum();
        h.assertTrue(buckets==3&&b.made==1,"Containers were lost or output duplicated");h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.MILK_BUCKET)==0,"Milk not consumed");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void pausedBillAndDestroyedStationDoNotConsumeItems(GameTestHelper h){
        var npc=prepare(h);storage(h);var b=bill(h,"bread",ProductionData.Mode.MAKE,1);npc.citizenData().inventory().add(new ItemStack(Items.WHEAT,3));
        var controller=new CitizenProductionController(npc);for(int i=0;i<5;i++)controller.tick(job(h,WorkType.CRAFTING,TABLE));
        b.paused=true;h.assertTrue(controller.tick(job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.DEFER,"Paused bill continued");
        b.paused=false;h.setBlock(TABLE,Blocks.AIR);h.assertTrue(controller.tick(job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.DEFER,"Destroyed workstation continued");
        h.assertTrue(b.made==0&&npc.citizenData().inventory().countPersonalItem(Items.WHEAT)==3,"Interrupted craft consumed items");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void twoWorkersCannotConsumeSameWarehouseIngredient(GameTestHelper h){
        var a=prepare(h);var b=h.spawn(dev.stonebanner.entity.ModEntities.HUMAN_NPC.get(),new BlockPos(2,1,2));for(var t:WorkType.values())b.setWorkPriority(t,WorkPriority.DISABLED);
        var chest=storage(h);chest.setItem(0,new ItemStack(Items.WHEAT,1));bill(h,"bread",ProductionData.Mode.MAKE,1);
        var ca=new CitizenProductionController(a);var cb=new CitizenProductionController(b);var job=job(h,WorkType.CRAFTING,TABLE);
        ca.tick(job);cb.tick(job);ca.tick(job);cb.tick(job);
        h.assertTrue(a.citizenData().inventory().countPersonalItem(Items.WHEAT)+b.citizenData().inventory().countPersonalItem(Items.WHEAT)==1,"Concurrent extraction duplicated ingredient");h.assertTrue(count(chest,Items.WHEAT)==0,"Extraction left copy in chest");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void specialWorkbenchesHaveValidManualCraftingMenus(GameTestHelper h){
        prepare(h);var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BenchTester"));
        var pos=h.absolutePos(TABLE);player.setPos(pos.getX(),pos.getY(),pos.getZ());
        for(var block:List.of(ProductionBlocks.CARPENTER.get(),ProductionBlocks.FORGE.get())){
            h.setBlock(TABLE,block);var menu=block.getMenuProvider(h.getBlockState(TABLE),h.getLevel(),pos).createMenu(1,player.getInventory(),player);
            h.assertTrue(menu.stillValid(player),"Custom table immediately closes vanilla crafting menu");h.setBlock(TABLE,Blocks.AIR);h.assertTrue(!menu.stillValid(player),"Destroyed table retains menu");
        }h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void fertilizerUsesRealBoneMealAndVanillaGrowth(GameTestHelper h){
        var npc=prepare(h);h.setBlock(SOIL,Blocks.FARMLAND);h.setBlock(SOIL.above(),Blocks.WHEAT);
        var d=ProductionData.forLevel(h.getLevel());long id=d.addField(UUID.randomUUID(),h.absolutePos(SOIL),h.absolutePos(SOIL),FarmCrop.WHEAT);d.field(id).fertilize=true;
        npc.citizenData().inventory().add(new ItemStack(Items.BONE_MEAL,2));
        h.assertTrue(finish(h,new CitizenProductionController(npc),job(h,WorkType.FARMING,SOIL))==CitizenProductionController.Result.COMPLETE,"Fertilizer did not grow crop");
        h.assertTrue(FarmCrop.WHEAT.block().getAge(h.getBlockState(SOIL.above()))>0&&npc.citizenData().inventory().countPersonalItem(Items.BONE_MEAL)==1,"Growth was free or meal was lost");d.field(id).paused=true;h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void protectedPlantingRestoresWorldAndKeepsSeed(GameTestHelper h){
        var npc=prepare(h);h.setBlock(SOIL,Blocks.FARMLAND);var d=ProductionData.forLevel(h.getLevel());long id=d.addField(UUID.randomUUID(),h.absolutePos(SOIL),h.absolutePos(SOIL),FarmCrop.WHEAT);
        npc.citizenData().inventory().add(new ItemStack(Items.WHEAT_SEEDS));
        h.startSequence().thenExecuteAfter(10,()->{
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> listener=e->{if(e.getPos().equals(h.absolutePos(SOIL.above())))e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try{
            var controller=new CitizenProductionController(npc);h.assertTrue(finish(h,controller,job(h,WorkType.FARMING,SOIL))==CitizenProductionController.Result.DEFER,"Protected planting succeeded");
            h.assertTrue(controller.reason()==WorkBlockReason.PROTECTED&&h.getBlockState(SOIL.above()).isAir(),"Cancelled crop not restored");h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.WHEAT_SEEDS)==1,"Cancelled placement consumed seed");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);d.field(id).paused=true;}}).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void foreignWorkerCannotCraftOwnedBill(GameTestHelper h){
        var npc=prepare(h);storage(h);var b=bill(h,"bread",ProductionData.Mode.MAKE,1);npc.citizenData().setRecruitedBy(UUID.randomUUID());npc.citizenData().inventory().add(new ItemStack(Items.WHEAT,3));
        h.assertTrue(new CitizenProductionController(npc).tick(job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.DEFER,"Foreign worker accepted owned bill");h.assertTrue(b.made==0,"Foreign bill changed");
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PlanTester"));player.setPos(npc.getX(),npc.getY(),npc.getZ());
        var snapshot=dev.stonebanner.network.packet.ProductionSnapshotPacket.forPlayer(player,false);h.assertTrue(snapshot.bills().isEmpty(),"Foreign bills exposed in snapshot");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void carpenterCraftsLaddersAndAcceptsDoorsWhileForgeAcceptsTools(GameTestHelper h){
        var npc=prepare(h);storage(h);var b=bill(h,"ladder",ProductionData.Mode.MAKE,3);h.setBlock(TABLE,ProductionBlocks.CARPENTER.get());npc.citizenData().inventory().add(new ItemStack(Items.STICK,7));
        h.assertTrue(finish(h,new CitizenProductionController(npc),job(h,WorkType.CRAFTING,TABLE))==CitizenProductionController.Result.COMPLETE,"Carpenter did not craft ladder");h.assertTrue(b.made==3&&npc.citizenData().inventory().countPersonalItem(Items.STICK)==0,"Ladder ingredients/output wrong");
        var door=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("minecraft","oak_door")).orElseThrow();
        h.assertTrue(ProductionService.stationAccepts(h.getLevel(),b.station,door),"Carpenter rejected vanilla REDSTONE doors");
        h.setBlock(TABLE,ProductionBlocks.FORGE.get());var sword=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("minecraft","iron_sword")).orElseThrow();h.assertTrue(ProductionService.stationAccepts(h.getLevel(),b.station,sword),"Forge rejected tools/weapons");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="production-rules")
    public static void disablingCraftingInterruptsActiveJobWithoutConsumingIngredients(GameTestHelper h){
        var npc=prepare(h);storage(h);var b=bill(h,"bread",ProductionData.Mode.MAKE,1);npc.setWorkPriority(WorkType.CRAFTING,WorkPriority.NORMAL);npc.citizenData().inventory().add(new ItemStack(Items.WHEAT,3));ProductionService.reconcile(h.getLevel());
        var board=CitizenJobBoard.forLevel(h.getLevel());var job=board.snapshot().stream().filter(j->j.workType()==WorkType.CRAFTING&&j.target().equals(b.station)).findFirst().orElseThrow();
        h.assertTrue(npc.workController().assign(job),"Craft assignment failed");for(int i=0;i<5;i++)npc.workController().tick();
        h.assertTrue(npc.workController().phase()==CitizenWorkController.WorkPhase.WORKING,"Production HUD phase still says travelling at the workstation");
        npc.setWorkPriority(WorkType.CRAFTING,WorkPriority.DISABLED);npc.workController().tick();h.assertTrue(!npc.workController().hasActiveJob()&&b.made==0,"Disabled crafting continued");
        h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.WHEAT)==3,"Disabled crafting consumed items");h.succeed();
    }
}
