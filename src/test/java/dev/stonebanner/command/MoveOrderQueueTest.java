package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveOrderQueueTest {
    @Test void activeMovementDoesNotConsumeQueuedOrder() {
        var queue = new MoveOrderQueue(); queue.offer(BlockPos.ZERO);
        assertNull(queue.takeWhenIdle(true));
        assertEquals(1, queue.size());
        assertEquals(BlockPos.ZERO, queue.takeWhenIdle(false));
    }

    @Test void alreadyReachedWaypointsAdvanceOneAtATime() {
        var queue = new MoveOrderQueue();
        queue.offer(BlockPos.ZERO); queue.offer(new BlockPos(1, 2, 3));
        assertEquals(BlockPos.ZERO, queue.takeWhenIdle(false));
        assertEquals(1, queue.size());
        assertEquals(new BlockPos(1, 2, 3), queue.takeWhenIdle(false));
        assertNull(queue.takeWhenIdle(false));
    }

    @Test void stopPreventsRestartOnNextIdleTick() {
        var queue = new MoveOrderQueue(); queue.offer(BlockPos.ZERO); queue.clear();
        assertNull(queue.takeWhenIdle(false));
    }
    @Test void waypointsExecuteInArrivalOrder() {
        var queue = new MoveOrderQueue();
        assertTrue(queue.offer(new BlockPos(1, 2, 3)));
        assertTrue(queue.offer(new BlockPos(4, 5, 6)));
        assertEquals(new BlockPos(1, 2, 3), queue.poll());
        assertEquals(new BlockPos(4, 5, 6), queue.poll());
        assertNull(queue.poll());
    }

    @Test void boundedQueueRejectsOverflowWithoutLosingOrders() {
        var queue = new MoveOrderQueue();
        for (int i = 0; i < 16; i++) assertTrue(queue.offer(new BlockPos(i, 0, 0)));
        assertFalse(queue.offer(new BlockPos(999, 0, 0)));
        assertEquals(16, queue.size());
        for (int i = 0; i < 16; i++) assertEquals(new BlockPos(i, 0, 0), queue.poll());
    }

    @Test void targetsAreCopiedFromMutablePositions() {
        var queue = new MoveOrderQueue();
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        queue.offer(pos); pos.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), queue.poll());
    }

    @Test void cancellationRemovesAllPendingOrders() {
        var queue = new MoveOrderQueue();
        queue.offer(BlockPos.ZERO); queue.clear();
        assertEquals(0, queue.size());
        assertNull(queue.poll());
        assertTrue(queue.offer(BlockPos.ZERO));
    }

    @Test void consumingAnOrderFreesCapacity() {
        var queue = new MoveOrderQueue();
        for (int i = 0; i < 16; i++) queue.offer(BlockPos.ZERO);
        queue.poll();
        assertTrue(queue.offer(BlockPos.ZERO));
        assertThrows(NullPointerException.class, () -> queue.offer(null));
    }
}
