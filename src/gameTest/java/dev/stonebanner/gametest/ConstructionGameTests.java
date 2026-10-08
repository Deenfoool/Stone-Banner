package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.construction.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class ConstructionGameTests {
    private static final BlockPos ORIGIN=new BlockPos(5,1,4),CHEST=new BlockPos(2,1,4);
    private static HumanNpcEntity prepare(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        var data=ConstructionData.forLevel(h.getLevel());var arena=new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(16,8,16)));
        for(var plan:data.plans())if(arena.contains(net.minecraft.world.phys.Vec3.atCenterOf(plan.origin)))data.edit(plan.owner,plan.id,"cancel");
        return npc;
    }
    private static ConstructionData.Plan plan(GameTestHelper h){
        var data=ConstructionData.forLevel(h.getLevel());return data.plan(data.add(UUID.randomUUID(),h.absolutePos(ORIGIN),Rotation.NONE));
    }
    private static CitizenJob job(GameTestHelper h){return CitizenJob.simple(1,WorkType.BUILDING,h.absolutePos(ORIGIN),h.getLevel().getGameTime());}
    private static int count(Container container,Item item){int count=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))count+=container.getItem(i).getCount();return count;}

    @GameTest(template="empty",batch="construction-cycle",timeoutTicks=9000)
    public static void workerBuildsFullCottageFromRealStorage(GameTestHelper h){
        var npc=prepare(h);var plan=plan(h);h.assertTrue(plan!=null,"Plan missing");
        h.setBlock(CHEST,Blocks.CHEST);StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(CHEST));var chest=(Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        int slot=0;for(var material:CottageBlueprint.materials().entrySet()){
            int remaining=material.getValue();while(remaining>0){int amount=Math.min(material.getKey().getMaxStackSize(),remaining);chest.setItem(slot++,new ItemStack(material.getKey(),amount));remaining-=amount;}
        }
        npc.citizenData().inventory().add(new ItemStack(Items.BREAD,16));npc.setWorkPriority(WorkType.BUILDING,WorkPriority.HIGH);ConstructionService.reconcile(h.getLevel());
        h.startSequence().thenWaitUntil(()->h.assertTrue(plan.completed,"Cottage unfinished: "+plan.status+" worker="+npc.workController().blockReason()))
            .thenExecute(()->{
                for(var placement:CottageBlueprint.placements())h.assertTrue(placement.matches(h.getLevel(),plan.origin,plan.rotation),"Missing cottage placement");
                for(var item:CottageBlueprint.materials().keySet())h.assertTrue(count(chest,item)+npc.citizenData().inventory().countPersonalItem(item)==0,"Unused or duplicated materials: "+item);
                h.assertTrue(npc.citizenData().experience(CitizenSkill.CONSTRUCTION)>0,"Construction practice missing");
            }).thenSucceed();
    }
    @GameTest(template="empty",batch="construction-rules",timeoutTicks=100)
    public static void missingSuppliesAndPausedPlanDoNotCreateBlocksOrConsumeBag(GameTestHelper h){
        var npc=prepare(h);var plan=plan(h);var controller=new CitizenConstructionController(npc);
        h.assertTrue(controller.tick(job(h))==CitizenConstructionController.Result.DEFER&&controller.reason()==WorkBlockReason.MATERIALS,"Missing material not deferred");
        npc.citizenData().inventory().add(new ItemStack(Items.OAK_PLANKS));plan.paused=true;
        h.assertTrue(controller.tick(job(h))==CitizenConstructionController.Result.DEFER,"Paused plan continued");
        h.assertTrue(h.getBlockState(ORIGIN).isAir()&&npc.citizenData().inventory().countPersonalItem(Items.OAK_PLANKS)==1,"Interrupted plan created block or lost material");h.succeed();
    }
    @GameTest(template="empty",batch="construction-furniture",timeoutTicks=100)
    public static void bedsAndDoorConsumeOneItemPerFurnitureAndCompletePlan(GameTestHelper h){
        var npc=prepare(h);var plan=plan(h);
        for(var placement:CottageBlueprint.placements())if(placement.item()!=Items.WHITE_BED&&placement.item()!=Items.OAK_DOOR)
            for(var cell:placement.cells())h.getLevel().setBlock(cell.at(plan.origin,plan.rotation),cell.oriented(plan.rotation),3);
        npc.citizenData().inventory().add(new ItemStack(Items.WHITE_BED,2));npc.citizenData().inventory().add(new ItemStack(Items.OAK_DOOR));
        var position=h.absolutePos(ORIGIN.offset(2,1,4));npc.setPos(position.getX()+.5,position.getY(),position.getZ()+.5);
        var controller=new CitizenConstructionController(npc);for(int i=0;i<200;i++)controller.tick(job(h));
        for(var placement:CottageBlueprint.placements())if(placement.item()==Items.WHITE_BED)h.assertTrue(placement.matches(h.getLevel(),plan.origin,plan.rotation),"Bed halves missing");
        position=h.absolutePos(ORIGIN.offset(2,1,1));npc.setPos(position.getX()+.5,position.getY(),position.getZ()+.5);npc.commandController().stop();
        for(int i=0;i<100;i++)controller.tick(job(h));
        h.assertTrue(plan.completed,"Plan did not complete");h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.WHITE_BED)==0&&npc.citizenData().inventory().countPersonalItem(Items.OAK_DOOR)==0,"Furniture cost incorrect");h.succeed();
    }
}
