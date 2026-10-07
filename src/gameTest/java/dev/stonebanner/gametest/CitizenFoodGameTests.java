package dev.stonebanner.gametest;

import dev.stonebanner.citizen.CitizenBrainState;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.ModEntities;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenFoodGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void immobileCitizenCanEatWithinReach(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(90);
        npc.citizenData().health().setInjury(dev.stonebanner.citizen.BodyPart.LEFT_LEG, dev.stonebanner.citizen.InjuryState.FRACTURE);
        npc.citizenData().health().setInjury(dev.stonebanner.citizen.BodyPart.RIGHT_LEG, dev.stonebanner.citizen.InjuryState.FRACTURE);
        var chest = chest(helper, new BlockPos(4, 1, 2), new ItemStack(Items.COOKED_BEEF), true);
        var start = npc.position();
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(npc.citizenData().needs().hunger() < 60, "Immobile NPC could not eat nearby food");
            helper.assertTrue(chest.isEmpty(), "Nearby food was not extracted");
            helper.assertTrue(npc.position().distanceToSqr(start) < 1, "Immobile NPC walked to food");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void foodRemovedDuringApproachIsNotCreatedAgain(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(90);
        var chest = chest(helper, new BlockPos(12, 1, 2), new ItemStack(Items.COOKED_BEEF), true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(npc.commandController().movementState() == CitizenBrainState.EAT,
                "Food approach not started"))
                .thenExecute(chest::clearContent)
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(npc.citizenData().needs().hunger() >= 90, "Missing food was recreated");
                    helper.assertTrue(chest.isEmpty(), "Removed food reappeared");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void explicitWorkInterruptsNonCriticalFoodTrip(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(65);
        npc.setWorkPriority(dev.stonebanner.citizen.WorkType.FORESTRY, dev.stonebanner.citizen.WorkPriority.NORMAL);
        var chest = chest(helper, new BlockPos(12, 1, 2), new ItemStack(Items.COOKED_BEEF), true);
        BlockPos tree = new BlockPos(2, 1, 10);
        helper.setBlock(tree, Blocks.OAK_LOG);
        var board = dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(npc.foodController().isSeeking(), "Food trip not started"))
                .thenExecute(() -> {
                    long id = board.publish(dev.stonebanner.citizen.WorkType.FORESTRY, helper.absolutePos(tree), helper.getLevel().getGameTime());
                    helper.assertTrue(npc.workController().assign(board.job(id).orElseThrow()), "Work rejected");
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!npc.foodController().isSeeking(), "Food trip blocked explicit work");
                    helper.assertTrue(npc.workController().hasActiveJob(), "Explicit work did not continue");
                    helper.assertTrue(chest.getItem(0).getCount() == 1, "Canceled trip consumed food");
                }).thenSucceed();
    }

    private static Container chest(GameTestHelper helper, BlockPos pos, ItemStack food, boolean register) {
        helper.setBlock(pos, Blocks.CHEST);
        var chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        chest.setItem(0, food);
        if (register) StorageData.forLevel(helper.getLevel()).register(helper.getLevel(), helper.absolutePos(pos));
        return chest;
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hungryCitizenFetchesRealFood(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(90);
        var chest = chest(helper, new BlockPos(10, 1, 2), new ItemStack(Items.COOKED_BEEF), true);
        helper.assertTrue(!chest.getItem(0).isEmpty(), "Food removed before approach");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(npc.citizenData().needs().hunger() < 60, "NPC did not eat");
            helper.assertTrue(chest.getItem(0).isEmpty(), "Eating did not remove food");
            helper.assertTrue(npc.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(10, 1, 2)))) < 8,
                    "NPC consumed food remotely");
        }).thenExecuteAfter(5, () -> helper.assertTrue(!npc.foodController().isSeeking(), "Satisfied NPC retained food task")).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void twoCitizensCannotDuplicateLastPortion(GameTestHelper helper) {
        var first = CitizenQueueGameTests.prepare(helper);
        var second = helper.spawn(ModEntities.HUMAN_NPC.get(), new BlockPos(2, 1, 4));
        first.citizenData().needs().setHunger(90);
        second.citizenData().needs().setHunger(90);
        var chest = chest(helper, new BlockPos(8, 1, 3), new ItemStack(Items.COOKED_BEEF), true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(chest.getItem(0).isEmpty(), "No one collected food");
            int fed = (first.citizenData().needs().hunger() < 60 ? 1 : 0) + (second.citizenData().needs().hunger() < 60 ? 1 : 0);
            helper.assertTrue(fed == 1, "Last portion was duplicated or lost");
        }).thenExecuteAfter(30, () -> {
            int fed = (first.citizenData().needs().hunger() < 60 ? 1 : 0) + (second.citizenData().needs().hunger() < 60 ? 1 : 0);
            helper.assertTrue(fed == 1, "Empty storage fed a second citizen");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void blockedStorageDoesNotFeedRemotely(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(90);
        var pos = new BlockPos(10, 1, 2);
        var chest = chest(helper, pos, new ItemStack(Items.COOKED_BEEF), true);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
            for (int y = 0; y < 3; y++) helper.setBlock(pos.relative(direction).above(y), Blocks.STONE);
        helper.startSequence().thenExecuteAfter(65, () -> {
            helper.assertTrue(chest.getItem(0).getCount() == 1, "Food extracted through wall");
            helper.assertTrue(npc.citizenData().needs().hunger() >= 90, "NPC ate through wall");
            helper.assertTrue(!npc.commandController().hasActiveCommand(), "Blocked storage retained route");
            helper.assertTrue(npc.brainState() == CitizenBrainState.EAT, "Waiting NPC lost need state");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void manualMoveInterruptsNonCriticalFoodTrip(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(65);
        var chest = chest(helper, new BlockPos(12, 1, 2), new ItemStack(Items.COOKED_BEEF), true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(npc.foodController().isSeeking(), "Food trip not started"))
                .thenExecute(() -> helper.assertTrue(npc.issueCommand(new ActorCommand.MoveTo(helper.absolutePos(new BlockPos(2, 1, 12)))), "Manual move rejected"))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!npc.foodController().isSeeking(), "Manual command retained food trip");
                    helper.assertTrue(npc.commandController().movementState() == CitizenBrainState.MOVE, "Food trip overwrote manual move");
                    helper.assertTrue(chest.getItem(0).getCount() == 1, "Canceled trip removed food");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullInventoryKeepsFoodRemainderPhysical(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        for (int i = 0; i < 9; i++) npc.citizenData().inventory().add(new ItemStack(Items.COBBLESTONE, 64));
        npc.citizenData().needs().setHunger(90);
        var chest = chest(helper, new BlockPos(6, 1, 2), new ItemStack(Items.MUSHROOM_STEW), true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(npc.citizenData().needs().hunger() < 85, "Stew not eaten");
            helper.assertTrue(chest.getItem(0).isEmpty(), "Stew remained in chest");
            var bowls = helper.getLevel().getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(4),
                    item -> item.getItem().is(Items.BOWL));
            helper.assertTrue(bowls.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "Bowl lost or duplicated with full bag");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unregisteredChestIsNotFoodStorage(GameTestHelper helper) {
        var npc = CitizenQueueGameTests.prepare(helper);
        npc.citizenData().needs().setHunger(90);
        var chest = chest(helper, new BlockPos(4, 1, 2), new ItemStack(Items.COOKED_BEEF), false);
        helper.startSequence().thenExecuteAfter(60, () -> {
            helper.assertTrue(chest.getItem(0).getCount() == 1, "NPC looted unregistered chest");
            helper.assertTrue(npc.citizenData().needs().hunger() >= 90, "NPC ate from unregistered chest");
        }).thenSucceed();
    }
}
