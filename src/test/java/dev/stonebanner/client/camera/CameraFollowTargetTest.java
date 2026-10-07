package dev.stonebanner.client.camera;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CameraFollowTargetTest {
    private final UUID uuid = UUID.randomUUID();
    private final CameraFollowTarget target = new CameraFollowTarget(42, uuid);

    @Test void originalLoadedLivingEntityCanBeFollowed() {
        assertTrue(target.matches(42, uuid, true, true, 100));
    }

    @Test void reusedEntityIdCannotStealCamera() {
        assertFalse(target.matches(42, UUID.randomUUID(), true, true, 100));
        assertFalse(target.matches(43, uuid, true, true, 100));
    }

    @Test void deadOrUnloadedEntityIsRejected() {
        assertFalse(target.matches(42, uuid, false, true, 100));
        assertFalse(target.matches(42, uuid, true, false, 100));
    }

    @Test void loadedNeighbourhoodHasInclusiveLimit() {
        assertTrue(target.matches(42, uuid, true, true, 64 * 64));
        assertFalse(target.matches(42, uuid, true, true, 64 * 64 + .01));
    }

    @Test void invalidDistanceFailsClosed() {
        for (double distance : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertFalse(target.matches(42, uuid, true, true, distance));
        }
    }
}
