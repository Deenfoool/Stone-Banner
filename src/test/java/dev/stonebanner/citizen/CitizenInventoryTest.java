package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenInventoryTest {
    @Test
    void cargoOnlyMergesWhenOwnersMatchIncludingLegacyNull() {
        var inventory = new CitizenInventory();
        var first = java.util.UUID.randomUUID(); var second = java.util.UUID.randomUUID();
        inventory.addHaulCargo(new ItemStack(Items.STICK, 3), first);
        inventory.addHaulCargo(new ItemStack(Items.STICK, 5), second);
        inventory.addHaulCargo(new ItemStack(Items.STICK, 7));
        inventory.addHaulCargo(new ItemStack(Items.STICK, 2), first);
        var cargo = inventory.haulCargoSnapshot();
        assertEquals(3, cargo.size());
        assertEquals(5, cargo.get(0).stack().getCount()); assertEquals(first, cargo.get(0).owner());
        assertEquals(5, cargo.get(1).stack().getCount()); assertEquals(second, cargo.get(1).owner());
        assertEquals(7, cargo.get(2).stack().getCount()); assertEquals(null, cargo.get(2).owner());
    }

    @Test
    void ownerRoundTripsWithoutModifyingItemTags() {
        var inventory = new CitizenInventory(); var owner = java.util.UUID.randomUUID();
        var stack = new ItemStack(Items.STICK, 70); stack.getOrCreateTag().putString("Custom", "unchanged");
        inventory.addHaulCargo(stack, owner);
        var loaded = new CitizenInventory(); loaded.load(inventory.save());
        assertEquals(2, loaded.haulCargoSnapshot().size());
        for (var cargo : loaded.haulCargoSnapshot()) {
            assertEquals(owner, cargo.owner());
            assertTrue(ItemStack.isSameItemSameTags(stack, cargo.stack()));
            assertFalse(cargo.stack().getTag().hasUUID("CargoOwner"));
        }
    }

    @Test
    void partialDeliveryPreservesOwnerAndEmptySlotClearsIt() {
        var inventory = new CitizenInventory(); var owner = java.util.UUID.randomUUID();
        inventory.addHaulCargo(new ItemStack(Items.STICK, 4), owner);
        inventory.setHaulCargoStack(0, new ItemStack(Items.STICK, 2));
        assertEquals(owner, inventory.haulCargoSnapshot().get(0).owner());
        inventory.setHaulCargoStack(0, ItemStack.EMPTY);
        inventory.addHaulCargo(new ItemStack(Items.STICK, 3));
        assertEquals(null, inventory.haulCargoSnapshot().get(0).owner());
        inventory.clear(); inventory.addHaulCargo(new ItemStack(Items.STICK));
        assertEquals(null, inventory.haulCargoSnapshot().get(0).owner());
    }

    @Test
    void oldSaveRemainsUnownedCargoAndPersonalOwnerFieldIsIgnored() {
        var inventory = new CitizenInventory();
        inventory.addHaulCargo(new ItemStack(Items.STICK, 4));
        inventory.add(new ItemStack(Items.BREAD, 2));
        var saved = inventory.save();
        saved.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).putUUID("CargoOwner", java.util.UUID.randomUUID());
        var loaded = new CitizenInventory(); loaded.load(saved);
        assertEquals(null, loaded.haulCargoSnapshot().get(0).owner());
        assertEquals(2, loaded.countPersonalItem(Items.BREAD));
        assertFalse(loaded.save().getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).hasUUID("CargoOwner"));
    }

    @Test
    void fullInventoryDoesNotMixDifferentOwnersToMakeRoom() {
        var inventory = new CitizenInventory(); var owner = java.util.UUID.randomUUID();
        inventory.add(new ItemStack(Items.COBBLESTONE, 8*64));
        inventory.addHaulCargo(new ItemStack(Items.STICK, 3), owner);
        var remainder = inventory.addHaulCargo(new ItemStack(Items.STICK, 4), java.util.UUID.randomUUID());
        assertEquals(4, remainder.getCount()); assertEquals(3, inventory.stack(8).getCount());
        assertEquals(owner, inventory.haulCargoSnapshot().get(0).owner());
    }

    @Test
    void addUsesRealStackLimitsAndReturnsRemainder() {
        CitizenInventory inventory = new CitizenInventory();
        ItemStack remainder = inventory.add(new ItemStack(Items.BREAD, 70));

        assertEquals(64, inventory.stack(0).getCount());
        assertEquals(6, inventory.stack(1).getCount());
        assertTrue(remainder.isEmpty());
    }

    @Test
    void haulCargoUsesSameInventoryButDoesNotMergeWithPersonalStacks() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.add(new ItemStack(Items.COBBLESTONE, 12));
        ItemStack remainder = inventory.addHaulCargo(new ItemStack(Items.COBBLESTONE, 20));

        assertTrue(remainder.isEmpty());
        assertEquals(12, inventory.stack(0).getCount());
        assertEquals(20, inventory.stack(1).getCount());
        assertTrue(inventory.hasHaulCargo());
        assertEquals(1, inventory.haulCargoSnapshot().get(0).slot());
    }

    @Test
    void constructionMaterialsCountAndConsumeOnlyPersonalSlots() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.add(new ItemStack(Items.LADDER, 3));
        inventory.addHaulCargo(new ItemStack(Items.LADDER, 5));

        assertEquals(3, inventory.countPersonalItem(Items.LADDER));
        ItemStack removed = inventory.removePersonalItem(Items.LADDER, 1);
        assertTrue(removed.is(Items.LADDER));
        assertEquals(1, removed.getCount());
        assertEquals(2, inventory.countPersonalItem(Items.LADDER));
        assertEquals(5, inventory.haulCargoSnapshot().get(0).stack().getCount());
    }

    @Test
    void haulCargoMarkerRoundTripsThroughNbt() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.add(new ItemStack(Items.BREAD, 4));
        inventory.addHaulCargo(new ItemStack(Items.IRON_ORE, 7));

        CompoundTag saved = inventory.save();
        CitizenInventory loaded = new CitizenInventory();
        loaded.load(saved);

        assertTrue(loaded.stack(0).is(Items.BREAD));
        assertTrue(loaded.stack(1).is(Items.IRON_ORE));
        assertTrue(loaded.hasHaulCargo());
        CitizenInventory.HaulCargo cargo = loaded.haulCargoSnapshot().get(0);
        assertEquals(1, cargo.slot());
        assertEquals(7, cargo.stack().getCount());

        loaded.setHaulCargoStack(cargo.slot(), ItemStack.EMPTY);
        assertFalse(loaded.hasHaulCargo());
        assertTrue(loaded.stack(1).isEmpty());
    }

    @Test
    void inventoryRoundTripsThroughNbt() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.add(new ItemStack(Items.BREAD, 12));
        inventory.add(new ItemStack(Items.IRON_PICKAXE, 1));

        CompoundTag saved = inventory.save();
        CitizenInventory loaded = new CitizenInventory();
        loaded.load(saved);

        assertTrue(loaded.stack(0).is(Items.BREAD));
        assertEquals(12, loaded.stack(0).getCount());
        assertTrue(loaded.stack(1).is(Items.IRON_PICKAXE));
        assertEquals(1, loaded.stack(1).getCount());
    }

    @Test
    void invalidSlotReturnsEmptyStack() {
        CitizenInventory inventory = new CitizenInventory();
        assertTrue(inventory.stack(-1).isEmpty());
        assertTrue(inventory.stack(CitizenInventory.SLOT_COUNT).isEmpty());
    }
}
