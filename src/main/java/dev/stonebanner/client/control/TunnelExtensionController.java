package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationLimits;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.Optional;

/** Client-side two-click workflow: choose an active tunnel, then choose its new deep endpoint. */
public final class TunnelExtensionController {
    private static boolean active;
    private static ExcavationPlanSnapshotPacket.PlanSnapshot selectedPlan;
    private static BlockPos hover;

    private TunnelExtensionController() {
    }

    public static void activate() {
        active = true;
        selectedPlan = null;
        hover = null;
    }

    public static void deactivate() {
        active = false;
        selectedPlan = null;
        hover = null;
    }

    public static boolean isActive() {
        return active;
    }

    /** Cancel a local endpoint choice; the original tunnel remains unchanged. */
    public static void cancelSelection() { selectedPlan = null; hover = null; }

    public static boolean hasSelectedPlan() {
        return selectedPlan != null;
    }

    public static Optional<ExcavationPlanSnapshotPacket.PlanSnapshot> selectedPlan() {
        return Optional.ofNullable(selectedPlan);
    }

    public static void updatePreview(BlockPos pos) {
        if (active && selectedPlan != null && pos != null) {
            hover = pos.immutable();
        }
    }

    public static boolean click(BlockPos pos) {
        if (!active || pos == null) {
            return false;
        }
        if (selectedPlan == null) {
            selectedPlan = findTunnelAt(pos).orElse(null);
            hover = pos.immutable();
            return selectedPlan != null;
        }

        int length = extensionLength(selectedPlan, pos);
        if (length <= 0 || !previewAllowed(pos)) {
            hover = pos.immutable();
            return true;
        }
        StoneBannerNetwork.sendExtendExcavation(selectedPlan.id(), length);
        selectedPlan = null;
        hover = null;
        return true;
    }

    /** RMB-style rewind: selected plan -> no selected plan. A second RMB may deactivate the tool. */
    public static boolean undoSelectionStep() {
        if (selectedPlan == null) {
            return false;
        }
        selectedPlan = null;
        hover = null;
        return true;
    }

    public static int previewLength() {
        return selectedPlan == null || hover == null ? 0 : extensionLength(selectedPlan, hover);
    }

    public static boolean previewAllowed() {
        return hover != null && previewAllowed(hover);
    }

    private static boolean previewAllowed(BlockPos endpoint) {
        if (selectedPlan == null || endpoint == null) {
            return false;
        }
        int length = extensionLength(selectedPlan, endpoint);
        if (length <= 0 || length > DesignationLimits.MAX_AXIS_LENGTH) {
            return false;
        }
        BlockPos min = selectedPlan.min();
        BlockPos max = selectedPlan.max();
        if (selectedPlan.modeCode() == 1) {
            min = selectedPlan.step() > 0 ? min : new BlockPos(min.getX() - length, min.getY(), min.getZ());
            max = selectedPlan.step() > 0 ? new BlockPos(max.getX() + length, max.getY(), max.getZ()) : max;
        } else if (selectedPlan.modeCode() == 2) {
            min = selectedPlan.step() > 0 ? min : new BlockPos(min.getX(), min.getY(), min.getZ() - length);
            max = selectedPlan.step() > 0 ? new BlockPos(max.getX(), max.getY(), max.getZ() + length) : max;
        } else {
            return false;
        }
        return DesignationLimits.isAllowed(min, max);
    }

    static int extensionLength(ExcavationPlanSnapshotPacket.PlanSnapshot plan, BlockPos endpoint) {
        if (plan == null || endpoint == null) {
            return 0;
        }
        if (plan.modeCode() == 1) {
            int deep = plan.step() > 0 ? plan.max().getX() : plan.min().getX();
            return plan.step() > 0 ? endpoint.getX() - deep : deep - endpoint.getX();
        }
        if (plan.modeCode() == 2) {
            int deep = plan.step() > 0 ? plan.max().getZ() : plan.min().getZ();
            return plan.step() > 0 ? endpoint.getZ() - deep : deep - endpoint.getZ();
        }
        return 0;
    }

    private static Optional<ExcavationPlanSnapshotPacket.PlanSnapshot> findTunnelAt(BlockPos pos) {
        return ExcavationOverlayState.plans().stream()
                .filter(plan -> plan.modeCode() == 1 || plan.modeCode() == 2)
                .filter(plan -> contains(plan, pos))
                .min(Comparator.comparingLong(TunnelExtensionController::volume));
    }

    private static boolean contains(ExcavationPlanSnapshotPacket.PlanSnapshot plan, BlockPos pos) {
        return pos.getX() >= plan.min().getX() && pos.getX() <= plan.max().getX()
                && pos.getY() >= plan.min().getY() && pos.getY() <= plan.max().getY()
                && pos.getZ() >= plan.min().getZ() && pos.getZ() <= plan.max().getZ();
    }

    private static long volume(ExcavationPlanSnapshotPacket.PlanSnapshot plan) {
        long x = (long) plan.max().getX() - plan.min().getX() + 1L;
        long y = (long) plan.max().getY() - plan.min().getY() + 1L;
        long z = (long) plan.max().getZ() - plan.min().getZ() + 1L;
        return x * y * z;
    }
}
