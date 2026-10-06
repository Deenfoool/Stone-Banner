package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OreDiscoveryDataTest {
    @Test void permissionAndPerPlayerDismissalSurviveReload() {
        var data = new OreDiscoveryData(); var player = UUID.randomUUID();
        var first = new BlockPos(12, 34, -5); var second = first.east();
        var finding = data.record("minecraft:iron_ore", List.of(first, second, first));
        assertEquals(2, finding.positions().size());
        assertFalse(finding.approved());
        assertTrue(data.approve(finding.id())); assertTrue(data.hide(finding.id(), player));
        var loaded = OreDiscoveryData.load(data.save(new CompoundTag()));
        var restored = loaded.at(second).orElseThrow();
        assertEquals(finding.id(), restored.id()); assertTrue(restored.approved());
        assertTrue(restored.hiddenFor(player)); assertFalse(restored.hiddenFor(UUID.randomUUID()));
        assertEquals(1, loaded.findings().size());
    }
    @Test void newExposedBlocksRequireNewPermissionAndResurfaceDismissedFinding() {
        var data = new OreDiscoveryData(); var player = UUID.randomUUID();
        var pos = new BlockPos(3, 30, 4);
        var first = data.record("minecraft:iron_ore", List.of(pos));
        data.approve(first.id()); data.hide(first.id(), player);
        var updated = data.record("minecraft:iron_ore", List.of(pos, pos.east()));
        assertEquals(first.id(), updated.id()); assertFalse(updated.approved());
        assertFalse(updated.hiddenFor(player)); assertEquals(2, updated.positions().size());
    }
    @Test void unknownCommandsCannotCreateFindingsOrPermission() {
        var data = new OreDiscoveryData();
        assertFalse(data.approve(123)); assertFalse(data.hide(123, UUID.randomUUID()));
        assertTrue(data.findings().isEmpty());
    }
    @Test void exposureScanDoesNotCrossHiddenOreOrDifferentOre() {
        BlockPos first = new BlockPos(0, 30, 0), hidden = first.east(), beyond = hidden.east();
        var ore = Map.of(first, "iron", hidden, "iron", beyond, "iron", first.north(), "coal");
        var visible = Set.of(first, beyond, first.north());
        var found = ExcavationOreDiscovery.collectExposed(first, "iron", pos -> ore.getOrDefault(pos, ""), visible::contains);
        assertEquals(Set.of(first), found);
    }
    @Test void exposureScanIsBoundedAndIncludesOnlyConnectedVisibleCells() {
        BlockPos first = new BlockPos(0, 30, 0);
        var found = ExcavationOreDiscovery.collectExposed(first, "iron", pos ->
                pos.getY() == 30 && pos.getZ() == 0 && pos.getX() >= 0 && pos.getX() < 1000 ? "iron" : "", pos -> true);
        assertEquals(OreDiscoveryData.MAX_BLOCKS, found.size());
        assertTrue(found.stream().allMatch(pos -> pos.getX() < OreDiscoveryData.MAX_BLOCKS));
    }
    @Test void boundedHistoryNeverTurnsEvictedOreIntoPermission() {
        var data = new OreDiscoveryData(); var firstPos = new BlockPos(0, 30, 0);
        var first = data.record("minecraft:iron_ore", List.of(firstPos)); data.approve(first.id());
        for (int i = 1; i <= OreDiscoveryData.MAX_FINDINGS; i++)
            data.record("minecraft:iron_ore", List.of(new BlockPos(i * 2, 30, 0)));
        assertEquals(OreDiscoveryData.MAX_FINDINGS, data.findings().size());
        assertTrue(data.at(firstPos).isEmpty());
        assertFalse(data.record("minecraft:iron_ore", List.of(firstPos)).approved());
    }
    @Test void invalidPersistentResourceIdIsIgnored() {
        var root = new CompoundTag(); var list = new net.minecraft.nbt.ListTag(); var entry = new CompoundTag();
        entry.putLong("Id", 1); entry.putString("Block", "bad resource!");
        entry.putLongArray("Positions", new long[]{new BlockPos(0, 30, 0).asLong()});
        list.add(entry); root.put("Findings", list);
        assertTrue(OreDiscoveryData.load(root).findings().isEmpty());
    }
    @Test void legacyHiddenPositionCacheDoesNotRevealOreInJournal() {
        var old = new CompoundTag(); old.putLongArray("Reported", new long[]{new BlockPos(9, 30, 9).asLong()});
        assertTrue(OreDiscoveryData.load(old).findings().isEmpty());
    }
}
