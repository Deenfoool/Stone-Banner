package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Client-side interaction state for tactical drag designations. Server remains authoritative. */
public final class DesignationController {
    private static DesignationType activeType;
    private static BlockPos dragStart;
    private static BlockPos dragEnd;

    private DesignationController() {
    }

    public static boolean isActive() {
        return activeType != null;
    }

    public static Optional<DesignationType> activeType() {
        return Optional.ofNullable(activeType);
    }

    public static void cycleMode() {
        if (activeType == null) {
            activeType = DesignationType.CHOP;
        } else {
            activeType = switch (activeType) {
                case CHOP -> DesignationType.MINE;
                case MINE -> DesignationType.CANCEL;
                case CANCEL -> null;
            };
        }
        clearDrag();
    }

    public static void deactivate() {
        activeType = null;
        clearDrag();
    }

    public static boolean begin(BlockPos pos) {
        if (activeType == null || pos == null) {
            return false;
        }
        dragStart = pos.immutable();
        dragEnd = dragStart;
        return true;
    }

    public static void update(BlockPos pos) {
        if (dragStart != null && pos != null) {
            dragEnd = pos.immutable();
        }
    }

    public static boolean finish(BlockPos pos) {
        if (activeType == null || dragStart == null) {
            return false;
        }
        update(pos);
        BlockPos first = dragStart;
        BlockPos second = dragEnd == null ? dragStart : dragEnd;
        StoneBannerNetwork.sendDesignation(activeType, first, second);
        clearDrag();
        return true;
    }

    public static void cancelDrag() {
        clearDrag();
    }

    public static Optional<BlockPos> dragStart() {
        return Optional.ofNullable(dragStart);
    }

    public static Optional<BlockPos> dragEnd() {
        return Optional.ofNullable(dragEnd);
    }

    public static long previewVolume() {
        if (dragStart == null || dragEnd == null) {
            return 0L;
        }
        long sizeX = Math.abs((long) dragStart.getX() - dragEnd.getX()) + 1L;
        long sizeY = Math.abs((long) dragStart.getY() - dragEnd.getY()) + 1L;
        long sizeZ = Math.abs((long) dragStart.getZ() - dragEnd.getZ()) + 1L;
        return sizeX * sizeY * sizeZ;
    }

    private static void clearDrag() {
        dragStart = null;
        dragEnd = null;
    }
}
