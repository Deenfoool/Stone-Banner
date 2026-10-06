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
        assertEquals(1, inventory.firstHaulCargo().orElseThrow().slot());
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
        CitizenInventory.HaulCargo cargo = loaded.firstHaulCargo().orElseThrow();
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
