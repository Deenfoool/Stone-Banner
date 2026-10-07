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
