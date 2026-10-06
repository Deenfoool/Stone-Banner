package dev.stonebanner.client.control;

import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.network.packet.OreDiscoverySnapshotPacket.Finding;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Client mirror. Hidden findings remain addressable through the discovery journal. */
public final class OreDiscoveryState {
    private static List<Finding> findings = List.of();
    private static long highlightedId;
    private static ResourceLocation dimension;
    private static final Set<Long> dismissed = new HashSet<>();
    private OreDiscoveryState() {}
    public static void accept(List<Finding> snapshot) {
        checkWorld(); findings = List.copyOf(snapshot);
        dismissed.removeIf(id -> snapshot.stream().anyMatch(f -> f.id() == id && f.hidden()));
    }
    public static List<Finding> findings() { checkWorld(); return findings; }
    public static Optional<Finding> notification() {
        return findings().stream().filter(f -> !f.hidden() && !dismissed.contains(f.id())).findFirst();
    }
    public static void dismiss(long id) { dismissed.add(id); }
    public static void highlight(long id) { highlightedId = highlightedId == id ? 0 : id; }
    public static List<BlockPos> highlighted() {
        return findings().stream().filter(f -> f.id() == highlightedId).findFirst()
                .map(Finding::positions).orElse(List.of());
    }
    public static void checkWorld() {
        var level = Minecraft.getInstance().level;
        ResourceLocation current = level == null ? null : level.dimension().location();
        if (!Objects.equals(current, dimension)) { clear(); dimension = current; }
    }
    public static void clear() { RpgCameraController.clearFocus(); findings = List.of(); highlightedId = 0; dismissed.clear(); dimension = null; }
}
