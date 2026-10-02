package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenHomeTest {
    @Test
    void localHelperIsBoundToConfiguredChunkRadius() {
        CitizenData data = new CitizenData();
        data.home().assign("village-test", new BlockPos(0, 64, 0), 2);
        data.setParticipation(CitizenParticipation.LOCAL_HELPER);

        assertTrue(data.canTravelTo(new BlockPos(31, 64, 0)));
        assertFalse(data.canTravelTo(new BlockPos(64, 64, 0)));
    }

    @Test
    void companionCanTravelBeyondHomeTerritory() {
        CitizenData data = new CitizenData();
        data.home().assign("village-test", BlockPos.ZERO, 2);
        data.setParticipation(CitizenParticipation.COMPANION);

        assertTrue(data.canTravelTo(new BlockPos(256, 64, 0)));
    }
}
