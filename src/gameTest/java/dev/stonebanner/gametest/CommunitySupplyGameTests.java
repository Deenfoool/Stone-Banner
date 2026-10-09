package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.construction.*;
import dev.stonebanner.production.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunitySupplyGameTests {
    private static final BlockPos CHEST=new BlockPos(4,1,2),TARGET=new BlockPos(7,1,7);
    private static HumanNpcEntity prepare(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);
        // One synchronous scenario per isolated batch; never touches the client world.
        h.getLevel().getDataStorage().set("stonebanner_settlements",new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_construction",new ConstructionData());
        h.getLevel().getDataStorage().set("stonebanner_production",new ProductionData());
        for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        npc.setWorkPriority(WorkType.FARMING,WorkPriority.NORMAL);
        npc.setWorkPriority(WorkType.CRAFTING,WorkPriority.NORMAL);
        for(int x=1;x<15;x++)for(int z=1;z<15;z++)for(int y=1;y<5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        h.setBlock(CHEST,Blocks.CHEST);
        h.assertTrue(StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(CHEST))==StorageData.RegisterResult.ADDED,"Storage missing");
        return npc;
    }
    private static Container chest(GameTestHelper h){return (Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));}
    private static void join(GameTestHelper h,HumanNpcEntity npc,boolean foreign) {
        var owner=UUID.randomUUID();var data=SettlementData.forLevel(h.getLevel());
        h.assertTrue(data.create(owner,"Supply camp",npc.blockPosition().offset(foreign?96:0,0,0))==SettlementData.Result.CREATED,"Camp missing");
        h.assertTrue(data.addResident(owner,npc.getUUID())==SettlementData.Result.JOINED,"Resident missing");
    }
    private static CitizenJob job(GameTestHelper h,WorkType type){return CitizenJob.simple(1,type,h.absolutePos(TARGET),h.getLevel().getGameTime());}
    private static void assertItems(GameTestHelper h,HumanNpcEntity npc,Item item,int expectedBag,int expectedChest) {
        h.assertTrue(npc.citizenData().inventory().countPersonalItem(item)==expectedBag,"Incorrect personal supplies: "+item+" actual="+npc.citizenData().inventory().countPersonalItem(item)+" expected="+expectedBag);
        h.assertTrue(chest(h).getItem(0).is(item)&&chest(h).getItem(0).getCount()==expectedChest
                ||expectedChest==0&&chest(h).getItem(0).isEmpty(),"Supplies stolen, duplicated or lost");
    }
    // mode 0: own territory; 1: forbidden before selection; 2: membership changes before extraction.
    private static void builder(GameTestHelper h,int mode) {
        var npc=prepare(h);chest(h).setItem(0,new ItemStack(Items.OAK_PLANKS,4));
        h.assertTrue(ConstructionData.forLevel(h.getLevel()).add(UUID.randomUUID(),h.absolutePos(TARGET),Rotation.NONE)>0,"Plan missing");
        var controller=new CitizenConstructionController(npc);var job=job(h,WorkType.BUILDING);
        if(mode!=2)join(h,npc,mode==1);
        var first=controller.tick(job);
        if(mode!=1)h.assertTrue(first==CitizenConstructionController.Result.RUNNING,"Source not selected");
        if(mode==2)join(h,npc,true);
        var result=controller.tick(job);
        if(mode!=0)h.assertTrue(result==CitizenConstructionController.Result.DEFER&&controller.reason()==WorkBlockReason.MATERIALS,"Forbidden supplies not deferred");
        assertItems(h,npc,Items.OAK_PLANKS,mode==0?4:0,mode==0?0:4);
        h.assertTrue(h.getBlockState(TARGET).isAir(),"Fetch placed a free building block");
        h.succeed();
    }
    private static void production(GameTestHelper h,int mode) {
        var npc=prepare(h);
        h.setBlock(TARGET.offset(1,2,0),Blocks.GLOWSTONE);
        // Crop survival reads the asynchronously updated light engine. Allow it to settle,
        // just as the existing production fixtures do, before requesting seeds.
        h.runAfterDelay(10,()->productionSteps(h,npc,mode));
    }
    private static void productionSteps(GameTestHelper h,HumanNpcEntity npc,int mode) {
        if(mode!=2)join(h,npc,mode==1);
        var data=ProductionData.forLevel(h.getLevel());
        if(mode!=2){
            var soil=TARGET.below();
            long fieldId=data.addField(UUID.randomUUID(),h.absolutePos(soil),h.absolutePos(soil),FarmCrop.WHEAT);
            h.assertTrue(fieldId>0,"Field missing");
            data.edit(data.field(fieldId).owner,fieldId,"fertilize");
            for(var item:new Item[]{Items.IRON_HOE,Items.WHEAT_SEEDS,Items.BONE_MEAL}){
                h.setBlock(soil,item==Items.IRON_HOE?Blocks.DIRT:Blocks.FARMLAND);
                h.setBlock(TARGET,item==Items.BONE_MEAL?Blocks.WHEAT:Blocks.AIR);
                h.assertTrue(ProductionService.pendingFarm(h.getLevel(),data.field(fieldId),h.absolutePos(soil)),"Farm stage not actionable: "+item);
                chest(h).setItem(0,new ItemStack(item));
                var controller=new CitizenProductionController(npc);var farm=CitizenJob.simple(1,WorkType.FARMING,h.absolutePos(soil),h.getLevel().getGameTime());
                controller.tick(farm);var result=controller.tick(farm);
                if(mode==1)h.assertTrue(result==CitizenProductionController.Result.DEFER&&controller.reason()==WorkBlockReason.MATERIALS,"Foreign farm supply accepted: "+item+" result="+result+" reason="+controller.reason());
                assertItems(h,npc,item,mode==0?1:0,mode==0?0:1);
            }
        }
        h.setBlock(TARGET,Blocks.CRAFTING_TABLE);chest(h).setItem(0,new ItemStack(Items.OAK_PLANKS,4));
        h.assertTrue(data.addBill(UUID.randomUUID(),h.absolutePos(TARGET),new ResourceLocation("minecraft","stick"),ProductionData.Mode.MAKE,4)>0,"Bill missing");
        var controller=new CitizenProductionController(npc);var craft=job(h,WorkType.CRAFTING);
        var first=controller.tick(craft);
        if(mode!=1)h.assertTrue(first==CitizenProductionController.Result.RUNNING,"Craft source not selected");
        if(mode==2)join(h,npc,true);
        var result=controller.tick(craft);
        if(mode!=0)h.assertTrue(result==CitizenProductionController.Result.DEFER&&controller.reason()==WorkBlockReason.MATERIALS,"Foreign craft supply accepted");
        assertItems(h,npc,Items.OAK_PLANKS,mode==0?1:0,mode==0?3:4);
        h.succeed();
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_builder_own",timeoutTicks=100)
    public static void builderFetchesOwnMaterials(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {builder(h,0);
        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_builder_foreign",timeoutTicks=100)
    public static void builderRejectsForeignMaterials(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {builder(h,1);
        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_builder_recheck",timeoutTicks=100)
    public static void builderRechecksMembershipBeforeExtraction(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {builder(h,2);
        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_production_own",timeoutTicks=100)
    public static void farmerAndCrafterFetchOwnSupplies(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {production(h,0);
        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_production_foreign",timeoutTicks=100)
    public static void farmerAndCrafterRejectForeignSupplies(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {production(h,1);
        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_production_recheck",timeoutTicks=100)
    public static void crafterRechecksMembershipBeforeExtraction(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {production(h,2);
        });
    }
}
