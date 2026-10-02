package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationLimits;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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
                case MINE -> DesignationType.EXCAVATE;
                case EXCAVATE -> DesignationType.TUNNEL;
                case TUNNEL -> DesignationType.CLEAR;
                case CLEAR -> DesignationType.CANCEL;
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
        if (!DesignationLimits.isAllowed(first, second)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable(
                                "message.stonebanner.designation.too_large",
                                DesignationLimits.MAX_BLOCKS_PER_REQUEST,
                                DesignationLimits.MAX_AXIS_LENGTH
                        ),
                        true
                );
            }
            clearDrag();
            return false;
        }
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

    public static Optional<DesignationLimits.Dimensions> previewDimensions() {
        if (dragStart == null || dragEnd == null) {
            return Optional.empty();
        }
        return Optional.of(DesignationLimits.dimensions(dragStart, dragEnd));
    }

    public static boolean previewAllowed() {
        return dragStart == null || dragEnd == null || DesignationLimits.isAllowed(dragStart, dragEnd);
    }

    private static void clearDrag() {
        dragStart = null;
        dragEnd = null;
    }
}
