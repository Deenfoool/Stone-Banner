package dev.stonebanner.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Session-local groups contain stable identities, never transient network entity IDs. */
public final class CitizenControlGroups {
    public static final int GROUP_COUNT = 9;
    public static final int MAX_MEMBERS = 64;
    private final List<List<UUID>> groups = new ArrayList<>();

    public CitizenControlGroups() {
        clear();
    }

    public void save(int slot, Collection<UUID> members) {
        checkSlot(slot);
        var unique = new LinkedHashSet<UUID>();
        for (UUID member : members) {
            if (member != null && unique.size() < MAX_MEMBERS) unique.add(member);
        }
        groups.set(slot, List.copyOf(unique));
    }

    public List<UUID> members(int slot) {
        checkSlot(slot);
        return groups.get(slot);
    }

    public void clear() {
        groups.clear();
        for (int slot = 0; slot < GROUP_COUNT; slot++) groups.add(List.of());
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= GROUP_COUNT) throw new IllegalArgumentException("Invalid control group: " + slot);
    }
}
