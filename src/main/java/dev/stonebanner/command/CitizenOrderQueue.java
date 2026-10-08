package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

/**
 * FIFO of validated player orders, separate from A* path nodes and never persisted to NBT.
 * Entity targets use UUID to avoid following an unrelated entity after numeric ID reuse.
 */
public final class CitizenOrderQueue {
    public static final int LIMIT = 16;
    public enum Kind { MOVE, FOLLOW, ATTACK, WORK }
    public record Entry(Kind kind, BlockPos block, UUID target) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.MOVE || kind == Kind.WORK) != (block != null))
                throw new IllegalArgumentException("Block target required only for MOVE/WORK");
            if ((kind == Kind.FOLLOW || kind == Kind.ATTACK) != (target != null))
                throw new IllegalArgumentException("Entity UUID required only for FOLLOW/ATTACK");
            if (block != null) block = block.immutable();
        }
        public static Entry move(BlockPos p) { return new Entry(Kind.MOVE, p, null); }
        public static Entry work(BlockPos p) { return new Entry(Kind.WORK, p, null); }
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
}
