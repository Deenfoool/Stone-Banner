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
    @Test void interactionIsBlockOnlyAndQueuePreviewSurvivesClear() {
        var queue = new CitizenOrderQueue();
        var interact = CitizenOrderQueue.Entry.interact(new BlockPos(-4, 70, 8));
        var target = UUID.randomUUID();
        assertTrue(queue.offer(interact));
        assertTrue(queue.offer(CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.FOLLOW, target)));
        String text = CitizenOrderQueue.encodePreview(CitizenOrderQueue.Entry.move(new BlockPos(1, 70, 1)), queue.snapshot());
        var restored = CitizenOrderQueue.decodePreview(text);
        assertEquals(3, restored.size());
        assertTrue(restored.get(0).active());
        assertEquals(CitizenOrderQueue.Kind.MOVE, restored.get(0).entry().kind());
        assertEquals(interact, restored.get(1).entry());
        assertFalse(restored.get(1).active());
        assertEquals(target, restored.get(2).entry().target());
        queue.clear();
        assertEquals(3, restored.size());
        assertThrows(IllegalArgumentException.class,
                () -> CitizenOrderQueue.Entry.target(CitizenOrderQueue.Kind.INTERACT, target));
    }
    @Test void malformedPreviewIsIgnoredAndEncodedQueueIsBounded() {
        assertTrue(CitizenOrderQueue.decodePreview("bad;1:WORK:not_a_number;0:ATTACK:bad_uuid").isEmpty());
        assertTrue(CitizenOrderQueue.decodePreview("1:MOVE:" + "1".repeat(2100)).isEmpty());
        var previews = CitizenOrderQueue.decodePreview(
                CitizenOrderQueue.encodePreview(null, java.util.Collections.nCopies(20,
                        CitizenOrderQueue.Entry.interact(new BlockPos(3, 64, 4)))));
        assertEquals(CitizenOrderQueue.LIMIT, previews.size());
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
