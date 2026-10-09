package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenSleepGameTests {
    private static final BlockPos HEAD=new BlockPos(12,1,2);
    private static void bed(GameTestHelper h) {
        h.setBlock(HEAD.relative(Direction.WEST),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST));
        h.setBlock(HEAD,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD));
    }
    private static HumanNpcEntity sleeper(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);bed(h);npc.citizenData().needs().setFatigue(95);npc.sleepController().sleepSecond();return npc;
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_walk",timeoutTicks=250)
    public static void physicalBedSleepAndWakeReleasesOccupied(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);
        h.assertTrue(npc.sleepController().isSeeking()&&!npc.isSleeping(),"Sleep did not start with physical route");
        h.startSequence().thenExecuteAfter(20,()->h.assertTrue(npc.sleepController().isSeeking()&&npc.citizenData().needs().fatigue()>=95,"Walking to bed restored fatigue"))
                .thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"NPC never reached bed"))
                .thenExecute(()->{h.assertTrue(npc.getSleepingPos().filter(h.absolutePos(HEAD)::equals).isPresent()&&h.getBlockState(HEAD).getValue(BedBlock.OCCUPIED),"Wrong bed / not occupied");npc.citizenData().needs().setFatigue(24);})
                .thenExecuteAfter(2,()->h.assertTrue(!npc.isSleeping()&&!npc.sleepController().engaged()&&!h.getBlockState(HEAD).getValue(BedBlock.OCCUPIED),"Recovered NPC did not wake/release bed")).thenSucceed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_compete",timeoutTicks=100)
    public static void oneBedCannotBeReservedByTwoCitizens(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var first=sleeper(h);var second=h.spawn(ModEntities.HUMAN_NPC.get(),new BlockPos(3,1,3));second.setNoAi(true);second.citizenData().needs().setFatigue(95);second.sleepController().sleepSecond();
        h.assertTrue(first.sleepController().engaged()&&!second.sleepController().engaged(),"Two NPC reserved same bed");
        first.sleepController().cancel(true);second.sleepController().sleepSecond();
        // Retry cooldown is deliberately bounded; release is independently tested via a fresh waiter.
        var third=h.spawn(ModEntities.HUMAN_NPC.get(),new BlockPos(4,1,3));third.citizenData().needs().setFatigue(95);third.sleepController().sleepSecond();
        h.assertTrue(third.sleepController().engaged(),"Released bed cannot be claimed");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_destroy",timeoutTicks=150)
    public static void destroyedBedCancelsRouteWithoutRestoringFatigue(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);h.setBlock(HEAD,Blocks.AIR);
        h.runAfterDelay(2,()->{h.assertTrue(!npc.sleepController().engaged()&&!npc.commandController().hasActiveCommand()&&!npc.isSleeping(),"Destroyed bed kept reservation/route");h.succeed();});

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_command",timeoutTicks=100)
    public static void manualMoveCancelsSleepRoute(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);h.assertTrue(npc.issueCommand(new dev.stonebanner.command.ActorCommand.MoveTo(h.absolutePos(new BlockPos(5,1,4)))),"Manual order rejected");
        h.assertTrue(!npc.sleepController().engaged()&&!npc.isSleeping(),"Manual movement retained bed claim");
        npc.citizenData().needs().setFatigue(70);npc.sleepController().sleepSecond();
        var target=new BlockPos(6,1,2);h.setBlock(target,Blocks.OAK_LOG);var board=CitizenJobBoard.forLevel(h.getLevel());long id=board.publish(WorkType.FORESTRY,h.absolutePos(target),h.getLevel().getGameTime());
        h.assertTrue(npc.workController().assign(board.job(id).orElseThrow())&&!npc.sleepController().engaged(),"Explicit work did not cancel non-critical bed route");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_reload",timeoutTicks=300)
    public static void reloadRestartsReservationAndThreatWakesSleeper(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);
        h.startSequence().thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"Sleep not reached"))
                .thenExecute(()->{var tag=new net.minecraft.nbt.CompoundTag();npc.addAdditionalSaveData(tag);npc.readAdditionalSaveData(tag);h.assertTrue(!npc.isSleeping()&&!h.getBlockState(HEAD).getValue(BedBlock.OCCUPIED),"Reload retained phantom occupancy");})
                .thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"Reload did not reacquire bed"))
                .thenExecute(()->npc.citizenData().needs().setDanger(100))
                .thenExecuteAfter(2,()->h.assertTrue(!npc.isSleeping()&&!npc.sleepController().engaged(),"Danger did not wake sleeper")).thenSucceed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_invalid",timeoutTicks=100)
    public static void occupiedBrokenAndInaccessibleBedUseGroundFallback(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=CitizenQueueGameTests.prepare(h);bed(h);h.setBlock(HEAD,h.getBlockState(HEAD).setValue(BedBlock.OCCUPIED,true));npc.citizenData().needs().setFatigue(95);npc.sleepController().sleepSecond();
        h.assertTrue(!npc.sleepController().engaged()&&npc.brainState()==CitizenBrainState.SLEEP,"Occupied bed stolen");
        npc.sleepController().cancel(true);h.setBlock(HEAD,h.getBlockState(HEAD).setValue(BedBlock.OCCUPIED,false));h.setBlock(HEAD.relative(Direction.WEST),Blocks.AIR);npc.sleepController().sleepSecond();
        h.assertTrue(!npc.sleepController().engaged(),"Broken bed used");
        npc.sleepController().cancel(true);bed(h);
        var walls=java.util.List.of(new BlockPos(12,1,1),new BlockPos(12,1,3),new BlockPos(13,1,2),new BlockPos(11,1,1),new BlockPos(11,1,3),new BlockPos(10,1,2));
        for(var wall:walls){h.setBlock(wall,Blocks.STONE);h.setBlock(wall.above(),Blocks.STONE);}npc.sleepController().sleepSecond();
        h.assertTrue(!npc.sleepController().engaged(),"Unreachable bed reserved without a route");
        npc.sleepController().cancel(true);for(var wall:walls){h.setBlock(wall,Blocks.AIR);h.setBlock(wall.above(),Blocks.AIR);}
        npc.citizenData().health().setInjury(BodyPart.LEFT_LEG,InjuryState.FRACTURE);npc.citizenData().health().setInjury(BodyPart.RIGHT_LEG,InjuryState.FRACTURE);npc.sleepController().sleepSecond();
        h.assertTrue(!npc.sleepController().engaged()&&!npc.isSleeping(),"Immobile NPC teleported to distant bed");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_removal",timeoutTicks=250)
    public static void removalAndDestroyedSleepingBedReleaseSpace(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);
        h.startSequence().thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"First sleeper not arrived"))
                .thenExecute(()->{
                    npc.discard();h.assertTrue(!h.getBlockState(HEAD).getValue(BedBlock.OCCUPIED),"Removed sleeper left occupied bed");
                    var next=h.spawn(ModEntities.HUMAN_NPC.get(),HEAD.relative(Direction.NORTH));next.citizenData().needs().setFatigue(95);next.sleepController().sleepSecond();
                    h.assertTrue(next.isSleeping(),"Removal did not release lease");h.setBlock(HEAD,Blocks.AIR);
                    h.runAfterDelay(2,()->{h.assertTrue(!next.isSleeping()&&!next.sleepController().engaged(),"Destroyed sleeping bed kept pose/lease");h.succeed();});
                });

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_hunger",timeoutTicks=250)
    public static void criticalHungerWakesSleeperToEatRealFood(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=sleeper(h);npc.citizenData().inventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,3));
        h.startSequence().thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"Sleeper not arrived"))
                .thenExecute(()->npc.citizenData().needs().setHunger(90))
                .thenExecuteAfter(2,()->h.assertTrue(!npc.isSleeping()&&!h.getBlockState(HEAD).getValue(BedBlock.OCCUPIED),"Critical hunger retained bed"))
                .thenExecuteAfter(25,()->h.assertTrue(npc.citizenData().needs().hunger()<90,"Woken NPC did not consume personal food")).thenSucceed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="sleep_medicine",timeoutTicks=250)
    public static void walkingToBedDoesNotHealBodyButActualSleepDoes(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc=CitizenQueueGameTests.prepare(h);bed(h);npc.citizenData().health().setInjury(BodyPart.RIGHT_ARM,InjuryState.WOUNDED);npc.citizenData().health().treat(BodyPart.RIGHT_ARM,false);npc.sleepController().sleepSecond();
        h.startSequence().thenExecuteAfter(20,()->h.assertTrue(npc.sleepController().isSeeking()&&npc.citizenData().health().recoverySeconds(BodyPart.RIGHT_ARM)==60,"Walking treated body for free"))
                .thenWaitUntil(()->h.assertTrue(npc.isSleeping(),"Patient did not reach bed"))
                .thenExecuteAfter(20,()->h.assertTrue(npc.citizenData().health().recoverySeconds(BodyPart.RIGHT_ARM)<60,"Actual sleeping did not progress treatment")).thenSucceed();

        });
    }
}
