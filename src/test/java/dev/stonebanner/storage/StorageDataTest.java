package dev.stonebanner.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageDataTest {
    @Test
    void registeredPositionsRoundTripWithoutVirtualInventoryState() {
        CompoundTag root = new CompoundTag();
        root.putLongArray("Containers", new long[]{
                new BlockPos(10, 64, 10).asLong(),
                new BlockPos(-5, 20, 7).asLong()
        });

        StorageData loaded = StorageData.load(root);
        assertEquals(2, loaded.registeredCount());
        assertTrue(loaded.registeredPositions().contains(new BlockPos(10, 64, 10)));
        assertTrue(loaded.registeredPositions().contains(new BlockPos(-5, 20, 7)));

        CompoundTag saved = loaded.save(new CompoundTag());
        assertEquals(2, saved.getLongArray("Containers").length);
    }

    @Test
    void unregisterOnlyChangesKnownPositions() {
        CompoundTag root = new CompoundTag();
        BlockPos known = new BlockPos(1, 2, 3);
        root.putLongArray("Containers", new long[]{known.asLong()});

        StorageData loaded = StorageData.load(root);
        assertFalse(loaded.unregister(new BlockPos(9, 9, 9)));
        assertTrue(loaded.unregister(known));
        assertEquals(0, loaded.registeredCount());
    }
}
