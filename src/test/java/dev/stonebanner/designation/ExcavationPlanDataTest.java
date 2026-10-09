package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExcavationPlanDataTest {
    @Test
    void worldChangesInvalidateOnlyNearbyExcavationPlans() {
        ExcavationPlanData loaded = ExcavationPlanData.load(planRoot(
                planTag(1L, 0, 20, 0, 4, 24, 4),
                planTag(2L, 100, 20, 100, 104, 24, 104)
        ));

        assertEquals(1, loaded.markWorldChanged(new BlockPos(6, 23, 2)));
        assertEquals(1, loaded.dirtyPlanCount());
        assertEquals(0, loaded.markWorldChanged(new BlockPos(7, 23, 2)));
        assertEquals(1, loaded.dirtyPlanCount());
    }

    @Test
    void distantWorldChangesDoNotScheduleGlobalPlanReconciliation() {
        ExcavationPlanData loaded = ExcavationPlanData.load(planRoot(
                planTag(1L, 0, 20, 0, 4, 24, 4),
                planTag(2L, 100, 20, 100, 104, 24, 104)
        ));

        assertEquals(0, loaded.markWorldChanged(new BlockPos(50, 64, 50)));
        assertEquals(0, loaded.dirtyPlanCount());
    }

    @Test
    void legacyVerticalPlanLoadsAndSavesInGenericSliceFormat() {
        CompoundTag root = new CompoundTag();
        root.putLong("NextId", 9L);

        CompoundTag plan = new CompoundTag();
        plan.putLong("Id", 4L);
        plan.putInt("MinX", 1);
        plan.putInt("MinY", 20);
        plan.putInt("MinZ", 3);
        plan.putInt("MaxX", 5);
        plan.putInt("MaxY", 28);
        plan.putInt("MaxZ", 7);
        plan.putInt("CurrentY", 24);
        ListTag plans = new ListTag();
        plans.add(plan);
        root.put("Plans", plans);

        ExcavationPlanData loaded = ExcavationPlanData.load(root);
        assertEquals(1, loaded.activePlanCount());
        assertTrue(loaded.containsActiveTarget(new BlockPos(3, 24, 5)));
        assertTrue(loaded.containsActiveTarget(new BlockPos(3, 20, 5)));
        assertFalse(loaded.containsActiveTarget(new BlockPos(6, 24, 5)));
        assertFalse(loaded.containsActiveTarget(null));

        CompoundTag saved = loaded.save(new CompoundTag());
        assertEquals(9L, saved.getLong("NextId"));
        ListTag savedPlans = saved.getList("Plans", Tag.TAG_COMPOUND);
        assertEquals(1, savedPlans.size());
        CompoundTag savedPlan = savedPlans.getCompound(0);
        assertEquals("vertical", savedPlan.getString("Mode"));
        assertEquals("auto", savedPlan.getString("AccessMode"));
        assertEquals(24, savedPlan.getInt("CurrentSlice"));
        assertEquals(-1, savedPlan.getInt("Step"));
        assertEquals(20, savedPlan.getInt("MinY"));
        assertEquals(28, savedPlan.getInt("MaxY"));
    }

    @Test
    void explicitLadderAccessRoundTrips() {
        CompoundTag root = new CompoundTag();
        root.putLong("NextId", 6L);

        CompoundTag plan = new CompoundTag();
        plan.putLong("Id", 5L);
        plan.putInt("MinX", 10);
        plan.putInt("MinY", 20);
        plan.putInt("MinZ", 10);
        plan.putInt("MaxX", 14);
        plan.putInt("MaxY", 30);
        plan.putInt("MaxZ", 14);
        plan.putString("Mode", "vertical");
        plan.putString("AccessMode", "ladders");
        plan.putInt("CurrentSlice", 26);
        plan.putInt("Step", -1);
        ListTag plans = new ListTag();
        plans.add(plan);
        root.put("Plans", plans);

        CompoundTag saved = ExcavationPlanData.load(root).save(new CompoundTag());
        CompoundTag savedPlan = saved.getList("Plans", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("ladders", savedPlan.getString("AccessMode"));
        assertEquals(26, savedPlan.getInt("CurrentSlice"));
    }

    @Test
    void tunnelPlanFormatRoundTripsWithoutLosingDirection() {
        CompoundTag root = new CompoundTag();
        root.putLong("NextId", 3L);

        CompoundTag plan = new CompoundTag();
        plan.putLong("Id", 2L);
        plan.putInt("MinX", 10);
        plan.putInt("MinY", 40);
        plan.putInt("MinZ", 2);
        plan.putInt("MaxX", 30);
        plan.putInt("MaxY", 42);
        plan.putInt("MaxZ", 4);
        plan.putString("Mode", "tunnel_x");
        plan.putInt("CurrentSlice", 27);
        plan.putInt("Step", -1);
        ListTag plans = new ListTag();
        plans.add(plan);
        root.put("Plans", plans);

        CompoundTag saved = ExcavationPlanData.load(root).save(new CompoundTag());
        CompoundTag savedPlan = saved.getList("Plans", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("tunnel_x", savedPlan.getString("Mode"));
        assertEquals("auto", savedPlan.getString("AccessMode"));
        assertEquals(27, savedPlan.getInt("CurrentSlice"));
        assertEquals(-1, savedPlan.getInt("Step"));
    }



    @Test
    void duplicateSavedPlanIdsDoNotOverwriteSeparateMines() {
        var first = planTag(3L, 1, 30, 1, 3, 34, 3);
        var second = planTag(3L, 17, 30, 17, 20, 34, 20);
        var third = planTag(8L, 40, 30, 40, 43, 34, 43);
        var loaded = ExcavationPlanData.load(planRoot(first, second, third));
        assertEquals(3, loaded.activePlanCount(), "A duplicate identifier erased a real plan");
        assertTrue(loaded.containsActiveTarget(new BlockPos(2, 34, 2)));
        assertTrue(loaded.containsActiveTarget(new BlockPos(18, 34, 18)));
        assertTrue(loaded.containsActiveTarget(new BlockPos(41, 34, 41)));
        var saved = loaded.save(new CompoundTag());
        var all = saved.getList("Plans", Tag.TAG_COMPOUND);
        var ids = new java.util.HashSet<Long>();
        for (int i = 0; i < all.size(); i++)
            assertTrue(ids.add(all.getCompound(i).getLong("Id")), "Repaired plans still share a persistent ID");
        assertTrue(saved.getLong("NextId") > ids.stream().mapToLong(Long::longValue).max().orElse(0));
        assertEquals(3, ExcavationPlanData.load(saved).activePlanCount());
    }

    @Test
    void malformedMiningPlanCoordinatesAreRejectedWithoutWorldScanning() {
        var valid = planTag(2L, 2, 42, 2, 4, 46, 4);
        var missing = planTag(3L, 10, 42, 10, 12, 46, 12);
        missing.remove("MinX");
        var oversized = planTag(4L, 0, 1, 0, 10000, 1, 10000);
        var inverted = planTag(5L, 40, 60, 10, 30, 60, 12);
        var data = ExcavationPlanData.load(planRoot(valid, missing, oversized, inverted));
        assertEquals(1, data.activePlanCount());
        assertTrue(data.containsActiveTarget(new BlockPos(3, 46, 3)));
        assertFalse(data.containsActiveTarget(BlockPos.ZERO));
        assertFalse(data.containsActiveTarget(new BlockPos(50, 1, 50)));
    }

    @Test
    void invalidRestoredFrontierDoesNotReinterpretEarlierLayers() {
        var corrupt = planTag(4L, 0, 20, 0, 2, 22, 2);
        corrupt.putInt("CurrentSlice", 320);
        var valid = planTag(7L, 16, 20, 16, 18, 22, 18);
        var restored = ExcavationPlanData.load(planRoot(corrupt, valid));
        assertEquals(1, restored.activePlanCount());
        assertTrue(restored.containsActiveTarget(new BlockPos(17, 22, 17)));
        assertFalse(restored.containsActiveTarget(new BlockPos(1, 21, 1)));
        assertTrue(restored.save(new CompoundTag()).getLong("NextId") > 7);
    }

    @Test
    void excavationReconcileDoesNotFreezeAfterWorldClockRollback() {
        assertTrue(ExcavationPlanData.reconcileDue(Long.MIN_VALUE, 0));
        assertFalse(ExcavationPlanData.reconcileDue(1000, 1019));
        assertTrue(ExcavationPlanData.reconcileDue(1000, 1020));
        assertTrue(ExcavationPlanData.reconcileDue(1000, 5),
                "Rewinding world time must immediately permit another reconciliation");
    }

    @Test
    void verticalSliceRequiresEveryChunkIncludingNegativeCoordinates() {
        var view = new ExcavationPlanData.PlanView(-18, 30, 2, 18, 34, 17, 0, 0, 34, -1);
        var seen = new java.util.HashSet<String>();
        assertTrue(ExcavationPlanData.sliceChunksLoaded(view, (x, z) -> {
            seen.add(x + "," + z);
            return true;
        }));
        assertEquals(8, seen.size(), "Quarry spans 4 x 2 chunk columns");
        assertTrue(seen.contains("-2,0") && seen.contains("1,1"),
                "Floor division across negative/positive chunk boundaries failed");
        assertFalse(ExcavationPlanData.sliceChunksLoaded(view, (x, z) -> x != -1 || z != 1),
                "One unloaded column must defer the entire mining layer");
    }

    @Test
    void tunnelXOnlyRequiresCurrentCrossSectionNotWholeFutureShaft() {
        var tunnel = new ExcavationPlanData.PlanView(1, 30, -2, 80, 33, 18, 1, 0, 33, 1);
        var seen = new java.util.HashSet<String>();
        assertTrue(ExcavationPlanData.sliceChunksLoaded(tunnel, (x, z) -> {
            seen.add(x + "," + z);
            return true;
        }));
        assertEquals(java.util.Set.of("2,-1", "2,0", "2,1"), seen,
                "An X-tunnel checks only its current X chunk and Z cross-section");
        assertFalse(ExcavationPlanData.sliceChunksLoaded(tunnel, (x, z) -> z != 1),
                "An unloaded part of a tunnel cross-section blocks front advancement");
    }

    @Test
    void tunnelZOnlyRequiresCurrentCrossSection() {
        var tunnel = new ExcavationPlanData.PlanView(-17, 30, 1, 0, 34, 120, 2, 0, 48, 1);
        var seen = new java.util.HashSet<String>();
        assertTrue(ExcavationPlanData.sliceChunksLoaded(tunnel, (x, z) -> {
            seen.add(x + "," + z);
            return true;
        }));
        assertEquals(java.util.Set.of("-2,3", "-1,3", "0,3"), seen);
        assertFalse(ExcavationPlanData.sliceChunksLoaded(tunnel, (x, z) -> x != -1));
    }


    @Test
    void unknownExcavationModeDoesNotBecomeVerticalMining() {
        CompoundTag alien = planTag(6L, 1, 20, 1, 3, 24, 3);
        alien.putString("Mode", "tunnel_diagonal_v2");
        CompoundTag valid = planTag(9L, 18, 20, 18, 20, 24, 20);
        var loaded = ExcavationPlanData.load(planRoot(alien, valid));
        assertEquals(1, loaded.activePlanCount());
        assertFalse(loaded.containsActiveTarget(new BlockPos(2, 24, 2)),
                "Unknown tunnel semantics were silently reinterpreted as a quarry");
        assertTrue(loaded.containsActiveTarget(new BlockPos(19, 24, 19)));
        assertTrue(loaded.save(new CompoundTag()).getLong("NextId") > 9);
    }

    @Test
    void unknownAccessStrategyCannotSilentlyChangePhysicalRamps() {
        CompoundTag unsafe = planTag(4L, 1, 20, 1, 3, 24, 3);
        unsafe.putString("AccessMode", "future_bridge_v2");
        var loaded = ExcavationPlanData.load(planRoot(unsafe));
        assertEquals(0, loaded.activePlanCount(),
                "Unknown access rules must not turn into AUTO and remove supports");
    }

    @Test
    void corruptedSliceDirectionDoesNotAdvanceAQuarryOrTunnel() {
        CompoundTag verticalWrong = planTag(3L, 1, 20, 1, 3, 24, 3);
        verticalWrong.putInt("Step", 1);
        CompoundTag zeroTunnel = planTag(4L, 18, 20, 18, 22, 24, 20);
        zeroTunnel.putString("Mode", "tunnel_x");
        zeroTunnel.putInt("Step", 0);
        zeroTunnel.putInt("CurrentSlice", 18);
        CompoundTag valid = planTag(5L, 35, 20, 35, 37, 24, 37);
        var loaded = ExcavationPlanData.load(planRoot(verticalWrong, zeroTunnel, valid));
        assertEquals(1, loaded.activePlanCount());
        assertFalse(loaded.containsActiveTarget(new BlockPos(2, 24, 2)));
        assertFalse(loaded.containsActiveTarget(new BlockPos(19, 24, 19)));
        assertTrue(loaded.containsActiveTarget(new BlockPos(36, 24, 36)));
    }

    @Test
    void legacyQuarryWithCurrentYStillRestoresAsDescending() {
        CompoundTag old = planTag(7L, 2, 15, 2, 4, 21, 4);
        old.remove("Mode");
        old.remove("Step");
        old.remove("CurrentSlice");
        old.putInt("CurrentY", 19);
        var saved = ExcavationPlanData.load(planRoot(old)).save(new CompoundTag());
        var restored = saved.getList("Plans", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("vertical", restored.getString("Mode"));
        assertEquals(-1, restored.getInt("Step"));
        assertEquals(19, restored.getInt("CurrentSlice"));
    }


    @Test
    void directExcavationCreationChecksBoundsBeforeScanningTheWorld() {
        assertTrue(ExcavationPlanData.validCreateRequest(1L,
                new BlockPos(-16, 30, -16), new BlockPos(16, 33, 16)));
        assertFalse(ExcavationPlanData.validCreateRequest(1L,
                BlockPos.ZERO, new BlockPos(64, 0, 0)), "65-block-long shaft is prohibited");
        assertFalse(ExcavationPlanData.validCreateRequest(1L,
                BlockPos.ZERO, new BlockPos(63, 3, 63)), "16384-block quarry is prohibited");
        assertFalse(ExcavationPlanData.validCreateRequest(0L, BlockPos.ZERO, BlockPos.ZERO));
        assertFalse(ExcavationPlanData.validCreateRequest(Long.MAX_VALUE, BlockPos.ZERO, BlockPos.ZERO));
        assertFalse(ExcavationPlanData.validCreateRequest(1L, null, BlockPos.ZERO));
        assertFalse(ExcavationPlanData.validCreateRequest(1L, BlockPos.ZERO, null));
        assertTrue(ExcavationPlanData.validCreateRequest(1L,
                BlockPos.ZERO, new BlockPos(63, 0, 63)), "Exactly 4096 blocks must be allowed");
    }

    private static CompoundTag planRoot(CompoundTag... planTags) {
        CompoundTag root = new CompoundTag();
        root.putLong("NextId", planTags.length + 1L);
        ListTag plans = new ListTag();
        for (CompoundTag plan : planTags) {
            plans.add(plan);
        }
        root.put("Plans", plans);
        return root;
    }

    private static CompoundTag planTag(long id, int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ) {
        CompoundTag plan = new CompoundTag();
        plan.putLong("Id", id);
        plan.putInt("MinX", minX);
        plan.putInt("MinY", minY);
        plan.putInt("MinZ", minZ);
        plan.putInt("MaxX", maxX);
        plan.putInt("MaxY", maxY);
        plan.putInt("MaxZ", maxZ);
        plan.putString("Mode", "vertical");
        plan.putString("AccessMode", "ramp");
        plan.putInt("CurrentSlice", maxY);
        plan.putInt("Step", -1);
        return plan;
    }
}
