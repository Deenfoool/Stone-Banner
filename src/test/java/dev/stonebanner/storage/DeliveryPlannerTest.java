package dev.stonebanner.storage;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPlannerTest {
    @Test void blockedNearestDoesNotHideReachableAlternative() {
        assertEquals(Optional.of("far"), DeliveryPlanner.choose(List.of("near", "far"), 8,
                name -> name.equals("near") ? Optional.empty() : Optional.of(name)));
    }
    @Test void preservesNearestFirstPreference() {
        assertEquals(Optional.of(1), DeliveryPlanner.choose(List.of(1, 2, 3), 8, Optional::of));
    }
    @Test void stopsSearchingAfterSuccess() {
        AtomicInteger calls = new AtomicInteger();
        DeliveryPlanner.choose(List.of(1, 2, 3), 8, value -> { calls.incrementAndGet(); return Optional.of(value); });
        assertEquals(1, calls.get());
    }
    @Test void respectsBoundedRouteSearch() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(DeliveryPlanner.choose(List.of(1, 2, 3), 2, value -> {
            calls.incrementAndGet(); return value == 3 ? Optional.of(value) : Optional.empty();
        }).isEmpty());
        assertEquals(2, calls.get());
    }
    @Test void emptyOrDisabledSearchDoesNotCallPathfinder() {
        assertTrue(DeliveryPlanner.choose(List.of(), 8, value -> { fail(); return Optional.empty(); }).isEmpty());
        assertTrue(DeliveryPlanner.choose(List.of(1), 0, value -> { fail(); return Optional.empty(); }).isEmpty());
    }
    @Test void unknownHudStatusHasSafeFallback() {
        assertEquals(DeliveryStatus.IDLE, DeliveryStatus.byId(-1));
        assertEquals(DeliveryStatus.IDLE, DeliveryStatus.byId(999));
    }
}
