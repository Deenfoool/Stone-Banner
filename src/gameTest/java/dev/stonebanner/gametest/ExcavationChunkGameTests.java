package dev.stonebanner.gametest;

import dev.stonebanner.designation.ExcavationPlanData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** A persistent mining plan must never skip an unavailable physical layer. */
@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class ExcavationChunkGameTests {
    private static final BlockPos REMOTE = new BlockPos(2_000_000, 70, 2_000_000);

    private ExcavationChunkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "excavation-chunk")
    public static void unloadedQuarryAndTunnelFrontiersRemainPending(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(!level.hasChunkAt(REMOTE),
                "The remote excavation fixture must start with no loaded chunk");
        CompoundTag root = new CompoundTag();
        root.putLong("NextId", 4L);
        ListTag savedPlans = new ListTag();
        savedPlans.add(plan(1, "vertical",
                REMOTE.getX(), 68, REMOTE.getZ(),
                REMOTE.getX() + 1, 70, REMOTE.getZ() + 1,
                70, -1));
        savedPlans.add(plan(2, "tunnel_x",
                REMOTE.getX(), 68, REMOTE.getZ(),
                REMOTE.getX() + 16, 70, REMOTE.getZ() + 1,
                REMOTE.getX(), 1));
        savedPlans.add(plan(3, "tunnel_z",
                REMOTE.getX(), 68, REMOTE.getZ(),
                REMOTE.getX() + 1, 70, REMOTE.getZ() + 16,
                REMOTE.getZ(), 1));
        root.put("Plans", savedPlans);
        var data = ExcavationPlanData.load(root);

        assertUnchanged(helper, data);
        helper.startSequence().thenExecuteAfter(30, () ->
                assertUnchanged(helper, data)).thenSucceed();
    }

    private static void assertUnchanged(GameTestHelper helper, ExcavationPlanData data) {
        var level = helper.getLevel();
        data.reconcileIfDue(level);
        CompoundTag saved = data.save(new CompoundTag());
        ListTag plans = saved.getList("Plans", Tag.TAG_COMPOUND);
        helper.assertTrue(plans.size() == 3 && data.activePlanCount() == 3,
                "Unloaded work was silently interpreted as a completed excavation");
        helper.assertTrue(plans.getCompound(0).getInt("CurrentSlice") == 70,
                "Quarry advanced past an unloaded horizontal layer");
        helper.assertTrue(plans.getCompound(1).getInt("CurrentSlice") == REMOTE.getX(),
                "X tunnel skipped an unloaded cross-section");
        helper.assertTrue(plans.getCompound(2).getInt("CurrentSlice") == REMOTE.getZ(),
                "Z tunnel skipped an unloaded cross-section");
        helper.assertTrue(data.containsActiveTarget(REMOTE),
                "Persisted excavation stopped covering its unfinished target");
        helper.assertTrue(!level.hasChunkAt(REMOTE),
                "Mining reconciliation unexpectedly forced a distant chunk to load");
    }

    private static CompoundTag plan(long id, String mode,
                                    int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                    int currentSlice, int step) {
        var plan = new CompoundTag();
        plan.putLong("Id", id);
        plan.putInt("MinX", minX);
        plan.putInt("MinY", minY);
        plan.putInt("MinZ", minZ);
        plan.putInt("MaxX", maxX);
        plan.putInt("MaxY", maxY);
        plan.putInt("MaxZ", maxZ);
        plan.putString("Mode", mode);
        plan.putString("AccessMode", "auto");
        plan.putInt("CurrentSlice", currentSlice);
        plan.putInt("Step", step);
        return plan;
    }
}
