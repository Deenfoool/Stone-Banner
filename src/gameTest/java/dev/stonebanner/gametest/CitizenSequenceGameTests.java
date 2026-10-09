package dev.stonebanner.gametest;

import dev.stonebanner.command.CitizenOrderQueue;
import dev.stonebanner.citizen.CitizenBrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenSequenceGameTests {
    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void permittedRouteCannotDetourOutsideBoundary(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        prepare(h);
        for(int z=1;z<=5;z++)for(int y=1;y<=4;y++)h.setBlock(new BlockPos(4,y,z),Blocks.STONE);
        var start=h.absolutePos(new BlockPos(2,1,2));
        var goal=h.absolutePos(new BlockPos(7,1,2));
        h.assertTrue(dev.stonebanner.navigation.BlockPathfinder.findPath(h.getLevel(),start,goal).isPresent(),
                "Fixture does not provide an unrestricted detour");
        int boundary=h.absolutePos(new BlockPos(0,1,5)).getZ();
        h.assertTrue(dev.stonebanner.navigation.BlockPathfinder.findPath(h.getLevel(),start,goal,
                pos->pos.getZ()<=boundary).isEmpty(), "Route crossed the forbidden boundary");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void changedHomeCancelsPlayerRouteButAllowsSystemReturn(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        var pos = h.absolutePos(new BlockPos(7, 1, 2));
        npc.citizenData().setParticipation(dev.stonebanner.citizen.CitizenParticipation.LOCAL_HELPER);
        h.assertTrue(npc.issueCommand(new dev.stonebanner.command.ActorCommand.MoveTo(pos)), "Initial move rejected");
        npc.citizenData().home().assign("relocated", pos.offset(64, 0, 64), 0);
        npc.commandController().tick();
        h.assertTrue(!npc.commandController().hasActiveCommand()
                && npc.commandController().status() == dev.stonebanner.citizen.CitizenCommandController.CommandStatus.UNREACHABLE,
                "Changed home retained a forbidden player route");
        h.assertTrue(npc.commandController().issueSystemMove(pos, CitizenBrainState.RETURN_HOME),
                "Travel boundary prevented system return");
        h.succeed();

        });
    }

    private static dev.stonebanner.entity.HumanNpcEntity prepare(GameTestHelper h) {
        var npc = CitizenQueueGameTests.prepare(h);
        for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        return npc;
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void criticalNeedsCancelMixedQueue(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        var commander = java.util.UUID.randomUUID();
        var move = CitizenOrderQueue.Entry.move(h.absolutePos(new BlockPos(7, 1, 2)));
        h.assertTrue(npc.orderSequence().enqueue(move, commander), "Initial move rejected");
        npc.citizenData().needs().setHunger(100);
        npc.orderSequence().tick();
        h.assertTrue(!npc.orderSequence().hasOrders(), "Critical hunger retained mixed queue");
        h.assertTrue(!npc.orderSequence().enqueue(move, commander), "Starving NPC accepted order");
        npc.citizenData().needs().setHunger(0);
        npc.citizenData().needs().setFatigue(100);
        h.assertTrue(!npc.orderSequence().enqueue(move, commander), "Exhausted NPC accepted order");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void criticalNeedsPreserveFoodRoute(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        var pos = h.absolutePos(new BlockPos(7, 1, 2));
        h.assertTrue(npc.orderSequence().enqueue(CitizenOrderQueue.Entry.move(pos), java.util.UUID.randomUUID()), "Move rejected");
        npc.citizenData().needs().setHunger(100);
        h.assertTrue(npc.commandController().issueSystemMove(pos, CitizenBrainState.EAT), "Food route rejected");
        npc.orderSequence().tick();
        h.assertTrue(!npc.orderSequence().hasOrders(), "Food route retained player queue");
        h.assertTrue(npc.commandController().hasActiveCommand()
                && npc.commandController().movementState() == CitizenBrainState.EAT, "Queue stopped food route");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void changedOwnerCancelsMixedQueue(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        var commander = java.util.UUID.randomUUID();
        var move = CitizenOrderQueue.Entry.move(h.absolutePos(new BlockPos(7, 1, 2)));
        npc.citizenData().setRecruitedBy(commander);
        h.assertTrue(npc.orderSequence().enqueue(move, commander), "Owner move rejected");
        npc.citizenData().setRecruitedBy(java.util.UUID.randomUUID());
        npc.orderSequence().tick();
        h.assertTrue(!npc.orderSequence().hasOrders() && !npc.commandController().hasActiveCommand(), "Old owner retained command");
        h.assertTrue(!npc.orderSequence().enqueue(move, commander), "Former owner queued order");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void discardedAttackTargetIsNotVictory(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        attackOutcome(h, false);

        });
    }

    @GameTest(setupTicks = 5, template = "empty", timeoutTicks = 100)
    public static void killedAttackTargetCompletesOrder(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        attackOutcome(h, true);

        });
    }

    private static void attackOutcome(GameTestHelper h, boolean killed) {
        var npc = prepare(h);
        var target = EntityType.HUSK.create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(7, 1, 2));
        target.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        target.setNoAi(true);
        h.getLevel().addFreshEntity(target);
        var commander = java.util.UUID.randomUUID();
        h.assertTrue(npc.orderSequence().enqueue(CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.ATTACK, target.getUUID()), commander), "Attack rejected");
        h.assertTrue(npc.orderSequence().enqueue(CitizenOrderQueue.Entry.move(h.absolutePos(new BlockPos(2, 1, 7))), commander), "Next order rejected");
        if (killed) target.setHealth(0);
        target.discard();
        npc.orderSequence().tick();
        h.assertTrue(killed ? npc.orderSequence().pendingCount() == 1 && npc.orderSequence().failure().isEmpty()
                : !npc.orderSequence().hasOrders() && npc.orderSequence().failure().equals("target_unreachable"),
                "Sequence confused disappearance with death");
        h.succeed();
    }
}
