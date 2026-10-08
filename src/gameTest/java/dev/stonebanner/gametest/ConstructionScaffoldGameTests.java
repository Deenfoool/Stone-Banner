package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.construction.*;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class ConstructionScaffoldGameTests {
    private static HumanNpcEntity prepare(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);var data=ConstructionData.forLevel(h.getLevel());
        var arena=new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(16,16,16)));
        for(var old:data.plans())if(arena.contains(net.minecraft.world.phys.Vec3.atCenterOf(old.origin))){
            for(var cell:new ArrayList<>(old.temporary.keySet())){h.getLevel().setBlock(cell,Blocks.AIR.defaultBlockState(),3);data.forget(cell);}
            data.edit(old.owner,old.id,"cancel");
        }return npc;
    }
    @GameTest(template="empty",batch="construction-scaffolds",timeoutTicks=3500)
    public static void jumpBuiltColumnAndBridgeAreReclaimedWithoutDuplicationOrFallInjury(GameTestHelper h){
        var npc=prepare(h);for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        npc.citizenData().inventory().add(new ItemStack(Items.BREAD,16));
        npc.citizenData().inventory().add(new ItemStack(Items.COBBLESTONE,64));npc.citizenData().inventory().add(new ItemStack(Items.LADDER,64));
        var placement=new CottageBlueprint.Placement(Items.OAK_PLANKS,List.of(new CottageBlueprint.Cell(new BlockPos(0,6,0),Blocks.OAK_PLANKS.defaultBlockState())));
        var blueprint=new BuildingBlueprint("test:high","High","high-v1",1,7,1,List.of(placement),Set.of(),"");
        var data=ConstructionData.forLevel(h.getLevel());var origin=h.absolutePos(new BlockPos(7,1,7));
        var plan=data.plan(data.add(UUID.randomUUID(),origin,Rotation.NONE,blueprint));h.assertTrue(plan!=null,"Missing plan");
        var scaffold=new ConstructionScaffoldController(npc);float health=npc.getHealth();var used=new HashSet<BlockPos>();
        h.startSequence().thenWaitUntil(()->{
            scaffold.access(plan,blueprint,placement);used.addAll(plan.temporary.keySet());
            h.assertTrue(plan.route!=null&&!scaffold.building(plan)&&npc.distanceToSqr(dev.stonebanner.navigation.BlockPathfinder.waypoint(h.getLevel(),plan.route.end().above()))<.12*.12,"Access not finished: "+plan.status+" pos="+npc.position());
            h.assertTrue(!plan.route.bridge().isEmpty(),"No temporary bridge was built");
            long stone=plan.temporary.values().stream().filter(s->s.is(Blocks.COBBLESTONE)).count();long ladder=plan.temporary.size()-stone;
            h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.COBBLESTONE)+stone==64,"Column/bridge created or lost material");
            h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.LADDER)+ladder==64,"Ladder material imbalance");
        }).thenExecute(()->{data.edit(plan.owner,plan.id,"cancel");scaffold.clear();})
          .thenWaitUntil(()->{
              var result=scaffold.cleanup(plan);
              h.assertTrue(result==CitizenConstructionController.Result.COMPLETE,"Cleanup unfinished: "+plan.status+" pos="+npc.position());
          }).thenExecuteAfter(10,()->{
              for(var p:used)h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Temporary block left behind: "+p);
              h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.COBBLESTONE)==64&&npc.citizenData().inventory().countPersonalItem(Items.LADDER)==64,"Cleanup lost or duplicated supplies");
              h.assertTrue(npc.getHealth()==health,"Controlled descent caused fall damage");
              data.finishCancel(plan);h.assertTrue(data.plan(plan.id)==null,"Cancelled plan retained after cleanup");
          }).thenSucceed();
    }
    @GameTest(template="empty",batch="construction-scaffolds",timeoutTicks=100)
    public static void foreignOccupantPreventsSupportRemovalAndRefund(GameTestHelper h){
        var npc=prepare(h);for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        var data=ConstructionData.forLevel(h.getLevel());var origin=h.absolutePos(new BlockPos(7,1,7));
        var plan=data.plan(data.add(UUID.randomUUID(),origin,Rotation.NONE));var base=h.absolutePos(new BlockPos(4,1,4));
        plan.route=new ScaffoldRoute(base,base.getY(),Direction.WEST,List.of());plan.cleanup=true;
        h.getLevel().setBlock(base,Blocks.COBBLESTONE.defaultBlockState(),3);plan.temporary.put(base,Blocks.COBBLESTONE.defaultBlockState());
        npc.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
        var pig=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,h.getLevel());pig.setNoAi(true);pig.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);h.getLevel().addFreshEntity(pig);
        var scaffold=new ConstructionScaffoldController(npc);
        for(int i=0;i<80;i++)scaffold.cleanup(plan);
        h.assertTrue(h.getLevel().getBlockState(base).is(Blocks.COBBLESTONE)&&plan.temporary.containsKey(base),"Occupied support removed");
        h.assertTrue(scaffold.reason()==WorkBlockReason.OCCUPIED,"Occupant did not block cleanup");
        h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.COBBLESTONE)==0,"Blocked cleanup refunded a block");
        pig.discard();data.forget(base);data.edit(plan.owner,plan.id,"cancel");h.succeed();
    }
    @GameTest(template="empty",batch="construction-scaffolds",timeoutTicks=100)
    public static void protectedBridgePlacementRollsBackWithoutMaterialCharge(GameTestHelper h){
        var npc=prepare(h);for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        var data=ConstructionData.forLevel(h.getLevel());var origin=h.absolutePos(new BlockPos(5,1,5));
        var placement=new CottageBlueprint.Placement(Items.OAK_PLANKS,List.of(new CottageBlueprint.Cell(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState())));
        var blueprint=new BuildingBlueprint("test:bridge","Bridge","bridge-v1",1,1,1,List.of(placement),Set.of(),"");
        var plan=data.plan(data.add(UUID.randomUUID(),origin,Rotation.NONE,blueprint));var base=h.absolutePos(new BlockPos(2,1,5));var bridge=base.east();
        plan.route=new ScaffoldRoute(base,base.getY(),Direction.WEST,List.of(bridge));
        h.getLevel().setBlock(base,Blocks.COBBLESTONE.defaultBlockState(),3);var ladder=base.west();var ladderState=Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST);
        h.getLevel().setBlock(ladder,ladderState,3);plan.temporary.put(base,Blocks.COBBLESTONE.defaultBlockState());plan.temporary.put(ladder,ladderState);
        npc.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);npc.citizenData().inventory().add(new ItemStack(Items.COBBLESTONE));
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> listener=e->{if(e.getPos().equals(bridge))e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try{
            var scaffold=new ConstructionScaffoldController(npc);for(int tick=0;tick<80;tick++)scaffold.access(plan,blueprint,placement);
            h.assertTrue(scaffold.reason()==WorkBlockReason.PROTECTED,"Placement protection did not apply");
            h.assertTrue(h.getLevel().getBlockState(bridge).isAir()&&!plan.temporary.containsKey(bridge),"Cancelled bridge persisted");
            h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.COBBLESTONE)==1,"Cancelled placement charged material");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);plan.temporary.clear();data.edit(plan.owner,plan.id,"cancel");}
        h.succeed();
    }
}
