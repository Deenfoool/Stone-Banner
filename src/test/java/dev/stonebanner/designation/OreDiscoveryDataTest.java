package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreDiscoveryDataTest {
    @Test
    void reportedOrePositionsRoundTripAndDeduplicate() {
        OreDiscoveryData data = new OreDiscoveryData();
        BlockPos first = new BlockPos(12, 34, -5);
        BlockPos second = new BlockPos(13, 34, -5);

        assertFalse(data.isReported(first));
        data.markReported(List.of(first, second, first));
        assertEquals(2, data.reportedCount());
        assertTrue(data.isReported(first));
        assertTrue(data.isReported(second));

        CompoundTag saved = data.save(new CompoundTag());
        OreDiscoveryData loaded = OreDiscoveryData.load(saved);
        assertEquals(2, loaded.reportedCount());
        assertTrue(loaded.isReported(first));
        assertTrue(loaded.isReported(second));
    }

    @Test
    void planBoundsDistinguishOreInsideAndOutsideDesignation() {
        ExcavationPlanData.PlanView plan = new ExcavationPlanData.PlanView(
                10, 20, 30,
                14, 24, 34,
                0, 24, -1
        );

        assertTrue(ExcavationOreDiscovery.insidePlan(plan, new BlockPos(12, 22, 32)));
        assertFalse(ExcavationOreDiscovery.insidePlan(plan, new BlockPos(15, 22, 32)));
        assertFalse(ExcavationOreDiscovery.insidePlan(plan, new BlockPos(12, 19, 32)));
    }
}
