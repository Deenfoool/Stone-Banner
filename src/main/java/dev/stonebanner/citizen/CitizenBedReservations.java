package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Short renewable leases; runtime only, never grants ownership of a village/player bed. */
public final class CitizenBedReservations {
    private record Lease(UUID citizen, long until) {}
    private final Map<BlockPos, Lease> leases = new HashMap<>();
    public boolean claim(BlockPos bed, UUID citizen, long now) {
        leases.entrySet().removeIf(e -> e.getValue().until <= now);
        var old = leases.get(bed);
        if (old != null && !old.citizen.equals(citizen)) return false;
        leases.put(bed.immutable(), new Lease(citizen, now + 60));
        return true;
    }
    public void release(BlockPos bed, UUID citizen) {
        var lease = leases.get(bed);
        if (lease != null && lease.citizen.equals(citizen)) leases.remove(bed);
    }
}
