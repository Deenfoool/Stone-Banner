package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunityDropGameTests {
    private static final BlockPos TARGET = new BlockPos(5, 1, 5);
    private record Fixture(HumanNpcEntity npc, UUID owner, BlockPos target) {}

    private static Fixture prepare(GameTestHelper h, boolean camp) {
        var npc = CitizenQueueGameTests.prepare(h);
        h.getLevel().getDataStorage().set("stonebanner_settlements", new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_storage", new StorageData());
        for (var type : WorkType.values()) npc.setWorkPriority(type, WorkPriority.DISABLED);
        var owner = UUID.randomUUID();
        if (camp) {
            var settlements = SettlementData.forLevel(h.getLevel());
            h.assertTrue(settlements.create(owner, "Drop camp", npc.blockPosition()) == SettlementData.Result.CREATED, "Camp missing");
            h.assertTrue(settlements.addResident(owner, npc.getUUID()) == SettlementData.Result.JOINED, "Member missing");
        }
        return new Fixture(npc, owner, h.absolutePos(TARGET));
    }

    private static ItemEntity drop(GameTestHelper h, Fixture f, UUID owner, int count) {
        var pos = f.target();
        var item = new ItemEntity(h.getLevel(), pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(Items.STICK, count));
        if (owner != null) item.getPersistentData().putUUID("SBProductionOwner", owner);
        h.getLevel().addFreshEntity(item);
        return item;
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_mixed", timeoutTicks=100)
    public static void mixedPileCollectsOwnOutputWithoutTouchingForeignOutput(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, true);
        var own = drop(h, f, f.owner(), 4);
        var foreign = drop(h, f, UUID.randomUUID(), 9);
        h.assertTrue(DroppedItemHauling.stacksAt(h.getLevel(), f.target(), f.npc()).stream().mapToInt(ItemStack::getCount).sum() == 4, "Selection includes foreign output");
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 4, "Own output not picked up");
        h.assertTrue(!own.isAlive() && foreign.isAlive() && foreign.getItem().getCount() == 9, "Foreign output changed");
        h.assertTrue(DroppedItemHauling.hasDroppedItems(h.getLevel(), f.target()), "Shared job validity lost");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_recheck", timeoutTicks=100)
    public static void membershipLossBetweenSelectionAndPickupPreservesDrop(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, true); var item = drop(h, f, f.owner(), 4);
        h.assertTrue(!DroppedItemHauling.stacksAt(h.getLevel(), f.target(), f.npc()).isEmpty(), "Own output not selectable");
        SettlementData.forLevel(h.getLevel()).removeResident(f.npc().getUUID());
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 0 && item.isAlive() && item.getItem().getCount() == 4, "Pickup ignored membership change");
        h.assertTrue(!f.npc().citizenData().inventory().hasHaulCargo(), "Unauthorized cargo created"); h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_job", timeoutTicks=100)
    public static void foreignOnlyPileIsNotAssignableAndJobSurvives(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, true); drop(h, f, UUID.randomUUID(), 4);
        var chest = new BlockPos(6, 1, 2); h.setBlock(chest, Blocks.CHEST);
        StorageData.forLevel(h.getLevel()).register(h.getLevel(), h.absolutePos(chest));
        f.npc().setWorkPriority(WorkType.HAULING, WorkPriority.NORMAL);
        DroppedItemHauling.publishIfNeeded(h.getLevel(), f.target());
        var board = CitizenJobBoard.forLevel(h.getLevel());
        var job = board.snapshot().stream().filter(j -> j.workType() == WorkType.HAULING && j.target().equals(f.target())).findFirst().orElseThrow();
        h.assertTrue(!f.npc().workController().assign(job), "Foreign-only job accepted");
        h.assertTrue(board.snapshot().stream().anyMatch(j -> j.id() == job.id()), "Unauthorized worker deleted shared job"); h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_legacy", timeoutTicks=100)
    public static void legacyProductionDropRequiresMatchingEmployer(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, false); var item = drop(h, f, f.owner(), 4);
        h.assertTrue(!DroppedItemHauling.mayCollect(f.npc(), item), "Unaffiliated NPC stole tagged output");
        f.npc().citizenData().setRecruitedBy(UUID.randomUUID());
        h.assertTrue(!DroppedItemHauling.mayCollect(f.npc(), item), "Other employer accepted");
        f.npc().citizenData().setRecruitedBy(f.owner());
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 4, "Matching legacy companion rejected"); h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_vanilla", timeoutTicks=100)
    public static void vanillaPickupOwnerIsRespectedButThrowerIsNotExclusive(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, false); var item = drop(h, f, null, 4);
        item.setThrower(f.npc().getUUID());
        h.assertTrue(DroppedItemHauling.mayCollect(f.npc(), item), "Thrower incorrectly treated as owner");
        item.setTarget(UUID.randomUUID());
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 0 && item.getItem().getCount() == 4, "Vanilla owner ignored");
        item.setTarget(f.npc().getUUID());
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 4, "Explicit NPC pickup owner rejected"); h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_partial", timeoutTicks=100)
    public static void partialPickupPreservesPhysicalRemainderAndOwner(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, true);
        f.npc().citizenData().inventory().clear();
        f.npc().citizenData().inventory().add(new ItemStack(Items.COBBLESTONE, 8*64));
        f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK, 63), f.owner());
        var item = drop(h, f, f.owner(), 4);
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 1, "Partial pickup count wrong");
        h.assertTrue(item.isAlive() && item.getItem().getCount() == 3 && item.getPersistentData().getUUID("SBProductionOwner").equals(f.owner()), "Remainder lost ownership or count"); h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template="empty", batch="community_drop_territory", timeoutTicks=100)
    public static void ordinaryDropsUseCampTerritoryWithLegacyFallback(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var f = prepare(h, false); var item = drop(h, f, null, 4);
        h.assertTrue(DroppedItemHauling.mayCollect(f.npc(), item), "Legacy ordinary drops rejected");
        var settlements = SettlementData.forLevel(h.getLevel());
        h.assertTrue(settlements.create(f.owner(), "Remote drop camp", f.npc().blockPosition().offset(96,0,0)) == SettlementData.Result.CREATED, "Camp missing");
        h.assertTrue(settlements.addResident(f.owner(), f.npc().getUUID()) == SettlementData.Result.JOINED, "Member missing");
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 0 && item.getItem().getCount() == 4, "Outside-camp drop collected");
        settlements.removeResident(f.npc().getUUID());
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(), f.target(), f.npc()) == 4, "Legacy fallback failed"); h.succeed();

        });
    }
}
