package dev.stonebanner.designation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationPlanDataTest {
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

        CompoundTag saved = loaded.save(new CompoundTag());
        assertEquals(9L, saved.getLong("NextId"));
        ListTag savedPlans = saved.getList("Plans", Tag.TAG_COMPOUND);
        assertEquals(1, savedPlans.size());
        CompoundTag savedPlan = savedPlans.getCompound(0);
        assertEquals("vertical", savedPlan.getString("Mode"));
        assertEquals(24, savedPlan.getInt("CurrentSlice"));
        assertEquals(-1, savedPlan.getInt("Step"));
        assertEquals(20, savedPlan.getInt("MinY"));
        assertEquals(28, savedPlan.getInt("MaxY"));
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
        assertEquals(27, savedPlan.getInt("CurrentSlice"));
        assertEquals(-1, savedPlan.getInt("Step"));
    }
}
