package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.citizen.*;
import dev.stonebanner.network.packet.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenInspectorGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void inspectorAndPriorityEditsValidateOwnerIdentityAndWorld(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"InspectorOwner"));p.setPos(npc.getX(),npc.getY(),npc.getZ());npc.citizenData().setRecruitedBy(p.getUUID());
        var world=h.getLevel().dimension().location();var request=new RequestCitizenInventoryPacket(npc.getId(),npc.getUUID(),world);
        h.assertTrue(RequestCitizenInventoryPacket.allowed(p,request),"Owner inspector denied");
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"InspectorGuest"));stranger.setPos(npc.getX(),npc.getY(),npc.getZ());
        h.assertTrue(!RequestCitizenInventoryPacket.allowed(stranger,request),"Foreign inventory leaked");
        h.assertTrue(!RequestCitizenInventoryPacket.allowed(p,new RequestCitizenInventoryPacket(npc.getId(),UUID.randomUUID(),world)),"Reused entity ID accepted");
        h.assertTrue(!RequestCitizenInventoryPacket.allowed(p,new RequestCitizenInventoryPacket(npc.getId(),npc.getUUID(),net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"))),"Wrong world accepted");
        var edit=new SetWorkPriorityPacket(npc.getId(),npc.getUUID(),world,WorkType.MINING.ordinal(),0);
        h.assertTrue(!SetWorkPriorityPacket.apply(stranger,edit)&&SetWorkPriorityPacket.apply(p,edit),"Priority authority mismatch");
        h.assertTrue(npc.citizenData().workPriority(WorkType.MINING)==WorkPriority.DISABLED,"Accepted edit not applied");
        npc.citizenData().setReturningToVillage(true);
        h.assertTrue(RequestCitizenInventoryPacket.allowed(p,request)&&!SetWorkPriorityPacket.apply(p,edit),"Returning owner cannot inspect or can edit");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=250)
    public static void forestryPracticeOnlyFollowsPhysicalCompletion(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);npc.citizenData().setSkill(CitizenSkill.FORESTRY,0);var target=new BlockPos(4,1,2);h.setBlock(target,Blocks.OAK_LOG);
        var board=CitizenJobBoard.forLevel(h.getLevel());long id=board.publish(WorkType.FORESTRY,h.absolutePos(target),h.getLevel().getGameTime());
        h.assertTrue(npc.workController().assign(board.job(id).orElseThrow()),"Forestry setup rejected");
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getBlockState(target).isAir(),"Tree not physically cut"))
            .thenExecute(()->h.assertTrue(npc.citizenData().experience(CitizenSkill.FORESTRY)==5,"Successful work did not grant correct XP"))
            .thenExecuteAfter(20,()->h.assertTrue(npc.citizenData().experience(CitizenSkill.FORESTRY)==5,"Completed job granted XP again")).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cancelledWorkDoesNotGrantPractice(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);var target=new BlockPos(4,1,2);h.setBlock(target,Blocks.OAK_LOG);var board=CitizenJobBoard.forLevel(h.getLevel());long id=board.publish(WorkType.FORESTRY,h.absolutePos(target),h.getLevel().getGameTime());npc.workController().assign(board.job(id).orElseThrow());
        npc.setWorkPriority(WorkType.FORESTRY,WorkPriority.DISABLED);
        h.runAfterDelay(20,()->{h.assertTrue(npc.citizenData().experience(CitizenSkill.FORESTRY)==0&&h.getBlockState(target).is(Blocks.OAK_LOG),"Cancellation trained or changed tree");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualCombatUsesInjuriesAndTrainsSuccessfulHitsOnly(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);npc.citizenData().setSkill(CitizenSkill.COMBAT,0);var healthy=h.spawn(EntityType.COW,new BlockPos(4,1,2));healthy.setNoAi(true);float hp=healthy.getHealth();
        h.assertTrue(npc.doHurtTarget(healthy),"Healthy strike failed");float regular=hp-healthy.getHealth();
        npc.citizenData().health().setInjury(BodyPart.LEFT_ARM,InjuryState.MISSING);npc.citizenData().health().setInjury(BodyPart.RIGHT_ARM,InjuryState.FRACTURE);
        var wounded=h.spawn(EntityType.COW,new BlockPos(5,1,2));wounded.setNoAi(true);hp=wounded.getHealth();h.assertTrue(npc.doHurtTarget(wounded),"Wounded strike failed");
        h.assertTrue(hp-wounded.getHealth()<regular,"Body efficiency ignored by actual attack");h.assertTrue(npc.citizenData().experience(CitizenSkill.COMBAT)==4,"Hits did not train combat");
        var invulnerable=h.spawn(EntityType.COW,new BlockPos(6,1,2));invulnerable.setInvulnerable(true);h.assertTrue(!npc.doHurtTarget(invulnerable)&&npc.citizenData().experience(CitizenSkill.COMBAT)==4,"Failed hit trained combat");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void sleepRecoversBelowTiredThresholdWithoutCargoInterruptions(GameTestHelper h){
        var npc=CitizenQueueGameTests.prepare(h);npc.citizenData().needs().setFatigue(70);npc.citizenData().inventory().addHaulCargo(new ItemStack(Items.COBBLESTONE,3));npc.setBrainState(CitizenBrainState.SLEEP);
        h.startSequence().thenExecuteAfter(120,()->{
            h.assertTrue(npc.citizenData().needs().fatigue()<65&&npc.brainState()==CitizenBrainState.SLEEP&&!npc.commandController().hasActiveCommand(),"Sleep ended early or cargo hijacked rest");
            var tag=new net.minecraft.nbt.CompoundTag();npc.addAdditionalSaveData(tag);npc.readAdditionalSaveData(tag);h.assertTrue(npc.brainState()==CitizenBrainState.SLEEP,"Reload lost recovery state");
            npc.citizenData().needs().setFatigue(24);
        }).thenExecuteAfter(20,()->h.assertTrue(npc.brainState()!=CitizenBrainState.SLEEP,"Recovered NPC stuck asleep")).thenSucceed();
    }
}
