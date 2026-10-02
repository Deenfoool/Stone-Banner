package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
