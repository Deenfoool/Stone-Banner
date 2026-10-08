package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.production.*;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunityDeliveryGameTests {
    private static final BlockPos CHEST = new BlockPos(4, 1, 2);
    private static HumanNpcEntity prepare(GameTestHelper h) {
        var npc = CitizenQueueGameTests.prepare(h);
        h.getLevel().getDataStorage().set("stonebanner_settlements", new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_production", new ProductionData());
        for (var type : WorkType.values()) npc.setWorkPriority(type, WorkPriority.DISABLED);
        npc.setWorkPriority(WorkType.HAULING, WorkPriority.NORMAL);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) for (int y = 1; y < 5; y++)
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        h.setBlock(CHEST, Blocks.CHEST);
        h.assertTrue(StorageData.forLevel(h.getLevel()).register(h.getLevel(), h.absolutePos(CHEST))
                == StorageData.RegisterResult.ADDED, "Storage missing");
        return npc;
    }
    private static Container chest(GameTestHelper h) {
        return (Container) h.getLevel().getBlockEntity(h.absolutePos(CHEST));
    }
    private static void join(GameTestHelper h, HumanNpcEntity npc, boolean foreign) {
        var data = SettlementData.forLevel(h.getLevel());
        var owner = UUID.randomUUID();
        h.assertTrue(data.create(owner, "Delivery camp", npc.blockPosition().offset(foreign ? 96 : 0, 0, 0))
                == SettlementData.Result.CREATED, "Camp missing");
        h.assertTrue(data.addResident(owner, npc.getUUID()) == SettlementData.Result.JOINED, "Resident missing");
    }
    private static void cargo(GameTestHelper h, int mode) {
        var npc = prepare(h);
        npc.citizenData().inventory().add(new ItemStack(Items.APPLE, 3));
        npc.citizenData().inventory().addHaulCargo(new ItemStack(Items.COBBLESTONE, 7));
        if (mode != 2) join(h, npc, mode == 1);
        var controller = npc.workController();
        controller.tick(); // Select and issue route.
        if (mode != 1) h.assertTrue(controller.deliveryStatus() == DeliveryStatus.TRAVELLING, "No route selected");
        controller.tick(); // Chest is within reach; advance to DEPOSITING.
        if (mode == 2) {
            h.assertTrue(controller.deliveryStatus() == DeliveryStatus.DEPOSITING, "Arrival did not precede membership change");
            join(h, npc, true);
        }
        controller.tick();
        int stored = 0;
        for (int i = 0; i < chest(h).getContainerSize(); i++) {
            var stack = chest(h).getItem(i);
            h.assertTrue(stack.isEmpty() || stack.is(Items.COBBLESTONE), "Personal item deposited");
            stored += stack.getCount();
        }
        int carried = npc.citizenData().inventory().haulCargoSnapshot().stream()
                .mapToInt(c -> c.stack().getCount()).sum();
        h.assertTrue(stored == (mode == 0 ? 7 : 0) && carried == (mode == 0 ? 0 : 7), "Cargo lost, duplicated or delivered outside camp");
        h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.APPLE) == 3, "Personal food changed");
        if (mode != 0) h.assertTrue(controller.deliveryStatus() == DeliveryStatus.WAITING_STORAGE, "Wrong forbidden-store status");
        h.succeed();
    }
    private static void craft(GameTestHelper h, boolean foreign) {
        var npc = prepare(h);
        join(h, npc, foreign);
        npc.setWorkPriority(WorkType.CRAFTING, WorkPriority.NORMAL);
        var station = new BlockPos(2, 1, 3);
        h.setBlock(station, Blocks.CRAFTING_TABLE);
        var data = ProductionData.forLevel(h.getLevel());
        long id = data.addBill(UUID.randomUUID(), h.absolutePos(station), new ResourceLocation("minecraft", "stick"), ProductionData.Mode.MAKE, 4);
        h.assertTrue(id > 0, "Bill missing");
        npc.citizenData().inventory().add(new ItemStack(Items.OAK_PLANKS, 2));
        var controller = new CitizenProductionController(npc);
        var job = CitizenJob.simple(1, WorkType.CRAFTING, h.absolutePos(station), h.getLevel().getGameTime());
        CitizenProductionController.Result result = null;
        for (int i = 0; i < 120 && result != CitizenProductionController.Result.COMPLETE && result != CitizenProductionController.Result.DEFER; i++)
            result = controller.tick(job);
        if (foreign) {
            h.assertTrue(result == CitizenProductionController.Result.DEFER && controller.reason() == WorkBlockReason.OUTPUT_FULL, "Foreign output store accepted");
            h.assertTrue(data.bill(id).made == 0 && npc.citizenData().inventory().countPersonalItem(Items.OAK_PLANKS) == 2, "Rejected craft spent ingredients or advanced bill");
            h.assertTrue(!npc.citizenData().inventory().hasHaulCargo(), "Rejected craft created output");
        } else {
            h.assertTrue(result == CitizenProductionController.Result.COMPLETE && data.bill(id).made == 4, "Own-store craft failed");
            h.assertTrue(npc.citizenData().inventory().countPersonalItem(Items.OAK_PLANKS) == 0, "Craft did not consume ingredients");
            h.assertTrue(npc.citizenData().inventory().haulCargoSnapshot().stream().mapToInt(c -> c.stack().getCount()).sum() == 4, "Craft output lost");
            h.assertTrue(npc.citizenData().inventory().haulCargoSnapshot().stream().allMatch(c -> data.bill(id).owner.equals(c.owner())), "Craft cargo lost bill owner");
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="community_delivery_own", timeoutTicks=100)
    public static void cargoReachesOwnStore(GameTestHelper h) { cargo(h, 0); }
    @GameTest(template="empty", batch="community_delivery_foreign", timeoutTicks=100)
    public static void cargoRetainedWhenOnlyForeignStoreExists(GameTestHelper h) { cargo(h, 1); }
    @GameTest(template="empty", batch="community_delivery_recheck", timeoutTicks=100)
    public static void membershipRecheckedAfterArrivalBeforeDeposit(GameTestHelper h) { cargo(h, 2); }
    @GameTest(template="empty", batch="community_output_own", timeoutTicks=100)
    public static void craftingAcceptsOwnOutputStore(GameTestHelper h) { craft(h, false); }
    @GameTest(template="empty", batch="community_output_foreign", timeoutTicks=100)
    public static void craftingPreservesIngredientsWithoutOwnOutputStore(GameTestHelper h) { craft(h, true); }
}
