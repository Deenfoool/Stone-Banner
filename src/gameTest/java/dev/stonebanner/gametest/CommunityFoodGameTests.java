package dev.stonebanner.gametest;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunityFoodGameTests {
    private static final BlockPos CHEST = new BlockPos(4, 1, 2);

    private static HumanNpcEntity prepare(GameTestHelper h) {
        var npc = CitizenQueueGameTests.prepare(h);
        // Separate batches reset only this isolated GameTest world's community data.
        h.getLevel().getDataStorage().set("stonebanner_settlements", new SettlementData());
        h.setBlock(CHEST, Blocks.CHEST);
        var pos = h.absolutePos(CHEST);
        h.assertTrue(StorageData.forLevel(h.getLevel()).registerManaged(h.getLevel(), List.of(pos), UUID.randomUUID(), true)
                == StorageData.RegisterResult.ADDED, "Storage registration failed");
        ((Container)h.getLevel().getBlockEntity(pos)).setItem(0, new ItemStack(Items.COOKED_BEEF));
        npc.citizenData().needs().setHunger(90);
        return npc;
    }

    private static void join(GameTestHelper h, HumanNpcEntity npc, BlockPos home) {
        var data = SettlementData.forLevel(h.getLevel());
        var owner = UUID.randomUUID();
        h.assertTrue(data.create(owner, "Food camp", home) == SettlementData.Result.CREATED, "Camp missing");
        h.assertTrue(data.addResident(owner, npc.getUUID()) == SettlementData.Result.JOINED, "Resident missing");
    }

    private static void check(GameTestHelper h, HumanNpcEntity npc, boolean eaten) {
        var chest = (Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        h.assertTrue(eaten ? chest.isEmpty() && npc.citizenData().needs().hunger() < 90
                : !chest.isEmpty() && chest.getItem(0).getCount() == 1 && npc.citizenData().needs().hunger() == 90,
                "Food was stolen, duplicated or not consumed");
    }

    @GameTest(setupTicks = 5, template = "empty", batch = "community_food_own", timeoutTicks = 100)
    public static void residentEatsFromOwnTerritoryRegardlessOfRegistrationManager(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        join(h, npc, npc.blockPosition());
        npc.foodController().eatSecond();
        npc.foodController().tick();
        check(h, npc, true);
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", batch = "community_food_foreign", timeoutTicks = 100)
    public static void residentOutsideCampDoesNotConsumeForeignNearbyFood(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        join(h, npc, npc.blockPosition().offset(96, 0, 0));
        npc.foodController().eatSecond();
        npc.foodController().tick();
        check(h, npc, false);
        h.assertTrue(!npc.commandController().hasActiveCommand(), "Resident routed to foreign supplies");
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", batch = "community_food_recheck", timeoutTicks = 100)
    public static void membershipChangedDuringApproachDoesNotConsumeOldFood(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        npc.foodController().eatSecond();
        join(h, npc, npc.blockPosition().offset(96, 0, 0));
        npc.foodController().tick();
        check(h, npc, false);
        h.succeed();

        });
    }

    @GameTest(setupTicks = 5, template = "empty", batch = "community_food_legacy", timeoutTicks = 100)
    public static void citizenWithoutCampRetainsLegacySharedNetwork(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var npc = prepare(h);
        npc.citizenData().setParticipation(dev.stonebanner.citizen.CitizenParticipation.COMPANION);
        var data = SettlementData.forLevel(h.getLevel());
        h.assertTrue(data.create(UUID.randomUUID(), "Other camp", npc.blockPosition()) == SettlementData.Result.CREATED,
                "Camp missing");
        npc.citizenData().home().assign("display-only", npc.blockPosition().offset(96, 0, 0), 0);
        npc.foodController().eatSecond();
        npc.foodController().tick();
        check(h, npc, true);
        h.succeed();

        });
    }
}
