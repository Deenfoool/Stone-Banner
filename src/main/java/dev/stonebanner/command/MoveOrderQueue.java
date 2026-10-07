package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Bounded pending waypoints, not navigation nodes. Transient and server-owned. */
public final class MoveOrderQueue {
    public static final int LIMIT = 16;
    private final Deque<BlockPos> pending = new ArrayDeque<>();
    public boolean offer(BlockPos target) {
        Objects.requireNonNull(target);
        if (pending.size() >= LIMIT) return false;
        pending.addLast(target.immutable());
        return true;
    }
    public BlockPos poll() { return pending.pollFirst(); }
    public BlockPos takeWhenIdle(boolean moving) { return moving ? null : poll(); }
    public int size() { return pending.size(); }
    public void clear() { pending.clear(); }
}
