package dev.stonebanner.storage;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StorageCapacityTest {
    @Test void fullFirstCargoTypeDoesNotHideSpaceForOtherCargo() {
        var container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        container.setItem(1, new ItemStack(Items.DIRT, 60));
        var stone = new ItemStack(Items.COBBLESTONE, 20);
        var dirt = new ItemStack(Items.DIRT, 10);
        assertFalse(StorageData.canAccept(container, stone));
        assertTrue(StorageData.canAcceptAny(container, List.of(stone, dirt)));
        assertEquals(20, stone.getCount()); assertEquals(10, dirt.getCount());
        assertEquals(60, container.getItem(1).getCount());
    }
    @Test void partialDepositConservesItemsAndDoesNotOverfill() {
        var container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.DIRT, 60));
        var remainder = new ItemStack(Items.DIRT, 10);
        assertTrue(StorageData.insertInto(container, remainder));
        assertEquals(64, container.getItem(0).getCount()); assertEquals(6, remainder.getCount());
        assertEquals(70, container.getItem(0).getCount() + remainder.getCount());
    }
    @Test void forbiddenSlotRejectsMergingAsWellAsEmptyInsertion() {
        var container = new SimpleContainer(1) { @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; } };
        container.setItem(0, new ItemStack(Items.DIRT, 60));
        var remainder = new ItemStack(Items.DIRT, 10);
        assertFalse(StorageData.canAccept(container, remainder));
        assertFalse(StorageData.insertInto(container, remainder));
        assertEquals(60, container.getItem(0).getCount()); assertEquals(10, remainder.getCount());
    }
}
