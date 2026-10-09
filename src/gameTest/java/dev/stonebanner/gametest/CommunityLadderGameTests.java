package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.construction.ConstructionData;
import dev.stonebanner.designation.ExcavationLadderTaskData;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunityLadderGameTests {
    private static final BlockPos CHEST = new BlockPos(4, 1, 2), TARGET = new BlockPos(2, 1, 4);
    // 0 own camp, 1 foreign store, 2 join foreign camp after route selection, 3 legacy NPC.
    private static void scenario(GameTestHelper h, int mode, boolean personal) {
        var npc = CitizenQueueGameTests.prepare(h);
        h.getLevel().getDataStorage().set("stonebanner_settlements", new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_construction", new ConstructionData());
        h.getLevel().getDataStorage().set("stonebanner_excavation_ladder_tasks", new ExcavationLadderTaskData());
        for (var type : WorkType.values()) npc.setWorkPriority(type, WorkPriority.DISABLED);
        npc.setWorkPriority(WorkType.BUILDING, WorkPriority.NORMAL);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) for (int y = 1; y < 5; y++)
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        h.setBlock(CHEST, Blocks.CHEST);
        h.setBlock(TARGET.east(), Blocks.STONE);
        h.assertTrue(StorageData.forLevel(h.getLevel()).register(h.getLevel(), h.absolutePos(CHEST))
                == StorageData.RegisterResult.ADDED, "Storage missing");
        var chest = (Container) h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        chest.setItem(0, new ItemStack(Items.LADDER, 4));
        if (personal) npc.citizenData().inventory().add(new ItemStack(Items.LADDER));
        if (mode < 2) join(h, npc, mode == 1);
        ExcavationLadderTaskData.forLevel(h.getLevel()).publish(h.getLevel(), h.absolutePos(TARGET), Direction.WEST);
        var job = CitizenJobBoard.forLevel(h.getLevel()).snapshot().stream()
                .filter(j -> j.workType() == WorkType.BUILDING && j.target().equals(h.absolutePos(TARGET))).findFirst().orElseThrow();
        boolean assigned = npc.workController().assign(job);
        if (mode == 1 && !personal) {
            h.assertTrue(!assigned && npc.workController().blockReason() == WorkBlockReason.MATERIALS, "Foreign ladders accepted");
        } else {
            h.assertTrue(assigned && npc.workController().hasActiveJob(), "Explicit ladder order did not initialise its phase");
            if (mode == 2) join(h, npc, true);
            npc.workController().tick();
            if (mode != 2) npc.workController().tick();
        }
        boolean completes = mode != 2 && (mode != 1 || personal);
        h.assertTrue(h.getBlockState(TARGET).is(Blocks.LADDER) == completes, "Ladder placement incorrect");
        int expectedChest = completes && !personal ? 3 : 4;
        h.assertTrue(chest.getItem(0).getCount() == expectedChest, "Ladder supplies lost, duplicated or stolen");
        h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.LADDER) == 0, "Unexpected personal ladder balance");
        h.assertTrue(!npc.workController().hasActiveJob(), "Ladder job stuck after completion/rejection");
        if (mode == 2) h.assertTrue(npc.workController().blockReason() == WorkBlockReason.MATERIALS, "Changed membership not reported");
        if (!completes) h.assertTrue(CitizenJobBoard.forLevel(h.getLevel()).job(job.id()).isPresent(), "Deferred task was deleted");
        h.succeed();
    }
    private static void join(GameTestHelper h, dev.stonebanner.entity.HumanNpcEntity npc, boolean foreign) {
        var data = SettlementData.forLevel(h.getLevel());
        var owner = UUID.randomUUID();
        h.assertTrue(data.create(owner, "Ladder camp", npc.blockPosition().offset(foreign ? 96 : 0, 0, 0))
                == SettlementData.Result.CREATED, "Camp missing");
        h.assertTrue(data.addResident(owner, npc.getUUID()) == SettlementData.Result.JOINED, "Resident missing");
    }
    @GameTest(setupTicks = 5, template="empty", batch="community_ladder_own", timeoutTicks=100)
    public static void explicitOrderFetchesAndPlacesOwnLadder(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> { scenario(h, 0, false);
        });
    }
    @GameTest(setupTicks = 5, template="empty", batch="community_ladder_foreign", timeoutTicks=100)
    public static void foreignLadderSupplyIsRejected(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> { scenario(h, 1, false);
        });
    }
    @GameTest(setupTicks = 5, template="empty", batch="community_ladder_recheck", timeoutTicks=100)
    public static void membershipRecheckedBeforeLadderExtraction(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> { scenario(h, 2, false);
        });
    }
    @GameTest(setupTicks = 5, template="empty", batch="community_ladder_legacy", timeoutTicks=100)
    public static void legacyWorkerRetainsSharedSupplyNetwork(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> { scenario(h, 3, false);
        });
    }
    @GameTest(setupTicks = 5, template="empty", batch="community_ladder_personal", timeoutTicks=100)
    public static void personalLadderDoesNotTouchForeignStore(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> { scenario(h, 1, true);
        });
    }
}
