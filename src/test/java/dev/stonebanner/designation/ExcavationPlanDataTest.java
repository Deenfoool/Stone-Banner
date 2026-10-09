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
