package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitizenOrderQueueTest {
    @Test void mixedOrdersKeepInsertionOrderAndImmutableBlocks() {
        var q=new CitizenOrderQueue();
        var block=new BlockPos.MutableBlockPos(1,65,2);
        var target=UUID.randomUUID();
        assertTrue(q.offer(CitizenOrderQueue.Entry.move(block)));
        block.set(50,1,50);
        assertTrue(q.offer(CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.FOLLOW,target)));
        assertTrue(q.offer(CitizenOrderQueue.Entry.work(new BlockPos(3,64,4))));
        assertTrue(q.offer(CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.ATTACK,target)));
        assertEquals(4,q.size());
        assertEquals(new BlockPos(1,65,2),q.poll().block());
        assertEquals(CitizenOrderQueue.Kind.FOLLOW,q.poll().kind());
        assertEquals(CitizenOrderQueue.Kind.WORK,q.poll().kind());
        assertEquals(target,q.poll().target());
        assertTrue(q.isEmpty());
    }
    @Test void maxSixteenAndStopClearsEverything() {
        var q=new CitizenOrderQueue();
        for(int i=0;i<CitizenOrderQueue.LIMIT;i++)
            assertTrue(q.offer(CitizenOrderQueue.Entry.move(new BlockPos(i,65,0))));
        assertFalse(q.offer(CitizenOrderQueue.Entry.move(new BlockPos(99,65,0))));
        assertEquals(CitizenOrderQueue.LIMIT,q.size());
        q.clear();
        assertEquals(0,q.size());
        assertNull(q.poll());
    }
    @Test void rejectsUnpairedTypeAndInvalidTargets() {
        assertThrows(IllegalArgumentException.class,
                ()->new CitizenOrderQueue.Entry(CitizenOrderQueue.Kind.MOVE,null,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                ()->new CitizenOrderQueue.Entry(CitizenOrderQueue.Kind.FOLLOW,new BlockPos(0,0,0),null));
        assertThrows(IllegalArgumentException.class,
                ()->CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.ATTACK,null));
    }
}
