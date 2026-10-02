package dev.stonebanner.client.control;

import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;

import java.util.List;

/** Client-only mirror of small excavation plan snapshots used by the world overlay. */
public final class ExcavationOverlayState {
    private static List<ExcavationPlanSnapshotPacket.PlanSnapshot> plans = List.of();

    private ExcavationOverlayState() {
    }

    public static void accept(List<ExcavationPlanSnapshotPacket.PlanSnapshot> snapshot) {
        plans = snapshot == null ? List.of() : List.copyOf(snapshot);
    }

    public static List<ExcavationPlanSnapshotPacket.PlanSnapshot> plans() {
        return plans;
    }

    public static boolean hasPlans() {
        return !plans.isEmpty();
    }

    public static void clear() {
        plans = List.of();
    }
}
