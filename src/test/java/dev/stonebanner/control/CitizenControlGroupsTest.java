package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitizenControlGroupsTest {
    @Test void allNineGroupsStartEmptyAndAreIndependent() {
        var groups = new CitizenControlGroups();
        var first = UUID.randomUUID();
        for (int slot = 0; slot < 9; slot++) assertTrue(groups.members(slot).isEmpty());
        groups.save(0, List.of(first));
        groups.save(8, List.of(UUID.randomUUID()));
        assertEquals(List.of(first), groups.members(0));
        assertTrue(groups.members(1).isEmpty());
        assertEquals(1, groups.members(8).size());
    }

    @Test void duplicatesAndNullsAreRemovedWithoutChangingOrder() {
        var groups = new CitizenControlGroups();
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        groups.save(0, Arrays.asList(first, null, second, first));
        assertEquals(List.of(first, second), groups.members(0));
    }

    @Test void groupHasSameLimitAsSelection() {
        var groups = new CitizenControlGroups();
        var members = new ArrayList<UUID>();
        for (int i = 0; i < 80; i++) members.add(UUID.randomUUID());
        groups.save(0, members);
        assertEquals(members.subList(0, 64), groups.members(0));
    }

    @Test void overwriteAndEmptySaveReplacePreviousGroup() {
        var groups = new CitizenControlGroups();
        groups.save(0, List.of(UUID.randomUUID()));
        var replacement = UUID.randomUUID();
        groups.save(0, List.of(replacement));
        assertEquals(List.of(replacement), groups.members(0));
        groups.save(0, List.of());
        assertTrue(groups.members(0).isEmpty());
    }

    @Test void snapshotsCannotBeMutatedAndResetClearsEveryGroup() {
        var groups = new CitizenControlGroups();
        var members = new ArrayList<>(List.of(UUID.randomUUID()));
        groups.save(0, members); members.clear();
        assertEquals(1, groups.members(0).size());
        assertThrows(UnsupportedOperationException.class, () -> groups.members(0).clear());
        groups.clear();
        for (int slot = 0; slot < 9; slot++) assertTrue(groups.members(slot).isEmpty());
    }

    @Test void invalidGroupNumbersAreRejected() {
        var groups = new CitizenControlGroups();
        for (int slot : new int[]{-1, 9}) {
            assertThrows(IllegalArgumentException.class, () -> groups.members(slot));
            assertThrows(IllegalArgumentException.class, () -> groups.save(slot, List.of()));
        }
    }
}
