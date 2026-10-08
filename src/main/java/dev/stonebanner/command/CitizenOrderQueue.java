package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * FIFO of validated player orders, separate from A* path nodes and never persisted to NBT.
 * Entity targets use UUID to avoid following an unrelated entity after numeric ID reuse.
 */
public final class CitizenOrderQueue {
    public static final int LIMIT = 16;
    public enum Kind { MOVE, FOLLOW, ATTACK, WORK, INTERACT }
    public record Entry(Kind kind, BlockPos block, UUID target) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.MOVE || kind == Kind.WORK || kind == Kind.INTERACT) != (block != null))
                throw new IllegalArgumentException("Block target required only for MOVE/WORK/INTERACT");
            if ((kind == Kind.FOLLOW || kind == Kind.ATTACK) != (target != null))
                throw new IllegalArgumentException("Entity UUID required only for FOLLOW/ATTACK");
            if (block != null) block = block.immutable();
        }
        public static Entry move(BlockPos p) { return new Entry(Kind.MOVE, p, null); }
        public static Entry work(BlockPos p) { return new Entry(Kind.WORK, p, null); }
        public static Entry interact(BlockPos p) { return new Entry(Kind.INTERACT, p, null); }
        public static Entry target(Kind kind, UUID id) { return new Entry(kind, null, id); }
    }
    private final Deque<Entry> pending = new ArrayDeque<>();
    public boolean offer(Entry order) {
        Objects.requireNonNull(order);
        if (pending.size() >= LIMIT) return false;
        pending.addLast(order);
        return true;
    }
    public Entry poll() { return pending.pollFirst(); }
    public int size() { return pending.size(); }
    public boolean isEmpty() { return pending.isEmpty(); }
    public void clear() { pending.clear(); }
    public List<Entry> snapshot() { return List.copyOf(pending); }

    /** Read-only view of the active order and at most sixteen queued commands. */
    public record Preview(Entry entry, boolean active) {}
    public static String encodePreview(Entry active, List<Entry> waiting) {
        StringJoiner out = new StringJoiner(";");
        if (active != null) out.add(previewToken(active, true));
        for (Entry entry : waiting.stream().limit(LIMIT).toList()) out.add(previewToken(entry, false));
        return out.toString();
    }
    private static String previewToken(Entry entry, boolean active) {
        return (active ? "1" : "0") + ":" + entry.kind().name() + ":"
                + (entry.block() == null ? entry.target() : entry.block().asLong());
    }
    public static List<Preview> decodePreview(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 2048) return List.of();
        List<Preview> result = new ArrayList<>();
        for (String token : encoded.split(";", -1)) {
            if (result.size() >= LIMIT + 1) break;
            try {
                String[] parts = token.split(":", 3);
                if (parts.length != 3 || !parts[0].equals("0") && !parts[0].equals("1")) continue;
                Kind kind = Kind.valueOf(parts[1]);
                Entry entry = kind == Kind.MOVE || kind == Kind.WORK || kind == Kind.INTERACT
                        ? new Entry(kind, BlockPos.of(Long.parseLong(parts[2])), null)
                        : Entry.target(kind, UUID.fromString(parts[2]));
                result.add(new Preview(entry, parts[0].equals("1")));
            } catch (IllegalArgumentException ignored) {
                // Broken or unknown visualization data never breaks world rendering.
            }
        }
        return List.copyOf(result);
    }
}
