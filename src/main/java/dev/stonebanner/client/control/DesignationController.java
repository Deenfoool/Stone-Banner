package dev.stonebanner.client.control;

import dev.stonebanner.designation.DesignationLimits;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Client-side three-click designation state. Server remains authoritative. */
public final class DesignationController {
    private static DesignationType activeType;
    private static BlockPos firstCorner;
    private static BlockPos footprintCorner;
    private static BlockPos hoverCorner;

    private DesignationController() {}

    public static boolean isActive() { return activeType != null; }
    public static Optional<DesignationType> activeType() { return Optional.ofNullable(activeType); }

    public static void activate(DesignationType type) {
        activeType = type;
        clearSelection();
    }

    public static void cycleMode() {
        if (activeType == null) activeType = DesignationType.CHOP;
        else activeType = switch (activeType) {
            case CHOP -> DesignationType.MINE;
            case MINE -> DesignationType.EXCAVATE;
            case EXCAVATE -> DesignationType.TUNNEL;
            case TUNNEL -> DesignationType.CLEAR;
            case CLEAR -> DesignationType.CANCEL;
            case CANCEL -> null;
        };
        clearSelection();
    }

    public static void deactivate() {
        activeType = null;
        clearSelection();
    }

    public static boolean click(BlockPos pos) {
        if (activeType == null || pos == null) return false;
        BlockPos immutable = pos.immutable();
        if (firstCorner == null) {
            firstCorner = immutable;
            hoverCorner = immutable;
            return true;
        }
        if (footprintCorner == null) {
            footprintCorner = horizontalCorner(immutable);
            hoverCorner = footprintCorner;
            return true;
        }
        BlockPos finalCorner = depthCorner(immutable);
        if (!DesignationLimits.isAllowed(firstCorner, finalCorner)) {
            showTooLargeMessage();
            return true;
        }
        StoneBannerNetwork.sendDesignation(activeType, firstCorner, finalCorner);
        clearSelection();
        return true;
    }

    public static void updatePreview(BlockPos pos) {
        if (activeType == null || firstCorner == null || pos == null) return;
        hoverCorner = footprintCorner == null ? horizontalCorner(pos) : depthCorner(pos);
    }

    public static boolean undoSelectionStep() {
        if (footprintCorner != null) {
            footprintCorner = null;
            hoverCorner = firstCorner;
            return true;
        }
        if (firstCorner != null) {
            clearSelection();
            return true;
        }
        return false;
    }

    public static boolean hasSelectionInProgress() { return firstCorner != null; }

    public static int completedClicks() {
        if (footprintCorner != null) return 2;
        return firstCorner == null ? 0 : 1;
    }

    public static SelectionStep nextStep() {
        return switch (completedClicks()) {
            case 0 -> SelectionStep.LENGTH;
            case 1 -> SelectionStep.WIDTH;
            default -> SelectionStep.DEPTH;
        };
    }

    public static Optional<BlockPos> selectionStart() { return Optional.ofNullable(firstCorner); }

    public static Optional<BlockPos> selectionEnd() {
        if (firstCorner == null) return Optional.empty();
        return Optional.of(hoverCorner == null ? firstCorner : hoverCorner);
    }

    public static Optional<DesignationLimits.Dimensions> previewDimensions() {
        BlockPos end = selectionEnd().orElse(null);
        if (firstCorner == null || end == null) return Optional.empty();
        return Optional.of(DesignationLimits.dimensions(firstCorner, end));
    }

    public static boolean previewAllowed() {
        BlockPos end = selectionEnd().orElse(null);
        return firstCorner == null || end == null || DesignationLimits.isAllowed(firstCorner, end);
    }

    private static BlockPos horizontalCorner(BlockPos pos) {
        return new BlockPos(pos.getX(), firstCorner.getY(), pos.getZ());
    }

    private static BlockPos depthCorner(BlockPos pos) {
        return new BlockPos(footprintCorner.getX(), pos.getY(), footprintCorner.getZ());
    }

    private static void showTooLargeMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.displayClientMessage(
                Component.translatable(
                        "message.stonebanner.designation.too_large",
                        DesignationLimits.MAX_BLOCKS_PER_REQUEST,
                        DesignationLimits.MAX_AXIS_LENGTH
                ),
                true
        );
    }

    private static void clearSelection() {
        firstCorner = null;
        footprintCorner = null;
        hoverCorner = null;
    }

    public enum SelectionStep {
        LENGTH("length"), WIDTH("width"), DEPTH("depth");
        private final String serializedName;
        SelectionStep(String serializedName) { this.serializedName = serializedName; }
        public String serializedName() { return serializedName; }
    }
}
