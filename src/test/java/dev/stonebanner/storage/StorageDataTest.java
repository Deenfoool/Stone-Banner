package dev.stonebanner.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageDataTest {
    @Test void managedRegistrationPersistsAndCannotBeRemovedByAnotherPlayer() {
        var pos=new BlockPos(4,64,5);var owner=java.util.UUID.randomUUID();
        var tag=new CompoundTag();tag.putLongArray("Containers",new long[]{pos.asLong()});
        var managers=new net.minecraft.nbt.ListTag();var entry=new CompoundTag();
        entry.putLong("Pos",pos.asLong());entry.putUUID("Player",owner);managers.add(entry);tag.put("Managers",managers);
        var loaded=StorageData.load(tag);var positions=java.util.List.of(pos);
        assertTrue(loaded.canManage(positions,owner,false));
        assertFalse(loaded.unregisterManaged(positions,java.util.UUID.randomUUID(),false));assertTrue(loaded.isRegistered(pos));
        var restored=StorageData.load(loaded.save(new CompoundTag()));assertEquals(owner,restored.manager(pos).orElseThrow());
        assertTrue(restored.unregisterManaged(positions,owner,false));assertFalse(restored.isRegistered(pos));assertTrue(restored.manager(pos).isEmpty());
    }
    @Test void legacyRegistrationsStaySharedAndRequireOperatorManagement() {
        var pos=BlockPos.ZERO;var tag=new CompoundTag();tag.putLongArray("Containers",new long[]{pos.asLong()});
        var loaded=StorageData.load(tag);var player=java.util.UUID.randomUUID();
        assertTrue(loaded.isRegistered(pos));assertTrue(loaded.manager(pos).isEmpty());
        assertFalse(loaded.canManage(java.util.List.of(pos),player,false));
        assertTrue(loaded.unregisterManaged(java.util.List.of(pos),player,true));
    }
    @Test void doubleContainerRemovalIsAtomicWhenOneHalfBelongsToAnotherManager() {
        var a=BlockPos.ZERO;var b=new BlockPos(1,0,0);var owner=java.util.UUID.randomUUID();
        var tag=new CompoundTag();tag.putLongArray("Containers",new long[]{a.asLong(),b.asLong()});
        var managers=new net.minecraft.nbt.ListTag();var own=new CompoundTag();own.putLong("Pos",a.asLong());own.putUUID("Player",owner);managers.add(own);
        var foreign=new CompoundTag();foreign.putLong("Pos",b.asLong());foreign.putUUID("Player",java.util.UUID.randomUUID());managers.add(foreign);tag.put("Managers",managers);
        var loaded=StorageData.load(tag);assertFalse(loaded.unregisterManaged(java.util.List.of(a,b),owner,false));
        assertTrue(loaded.isRegistered(a));assertTrue(loaded.isRegistered(b));
    }
    @Test void orphanAndMalformedManagerRecordsAreIgnored() {
        var tag=new CompoundTag();tag.putLongArray("Containers",new long[]{BlockPos.ZERO.asLong()});
        var entries=new net.minecraft.nbt.ListTag();var orphan=new CompoundTag();orphan.putLong("Pos",new BlockPos(2,0,0).asLong());orphan.putUUID("Player",java.util.UUID.randomUUID());entries.add(orphan);
        var malformed=new CompoundTag();malformed.putUUID("Player",java.util.UUID.randomUUID());entries.add(malformed);tag.put("Managers",entries);
        var loaded=StorageData.load(tag);assertTrue(loaded.manager(BlockPos.ZERO).isEmpty());assertTrue(loaded.manager(new BlockPos(2,0,0)).isEmpty());
    }
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
