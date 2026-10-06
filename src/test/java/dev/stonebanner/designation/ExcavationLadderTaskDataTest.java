package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcavationLadderTaskDataTest {
    @Test
    void taskRoundTripsAndKeepsFacing() {
        BlockPos target = new BlockPos(12, 41, -8);
        CompoundTag root = rootWithTask(target, Direction.NORTH);

        ExcavationLadderTaskData loaded = ExcavationLadderTaskData.load(root);
        ExcavationLadderTaskData.LadderTask task = loaded.task(target).orElseThrow();
        assertEquals(target, task.target());
        assertEquals(Direction.NORTH, task.facing());

        ExcavationLadderTaskData roundTripped = ExcavationLadderTaskData.load(loaded.save(new CompoundTag()));
        assertEquals(Direction.NORTH, roundTripped.task(target).orElseThrow().facing());
    }

    @Test
    void cancelIntersectingRemovesOnlyTasksInsideSelection() {
        BlockPos inside = new BlockPos(10, 30, 10);
        BlockPos outside = new BlockPos(20, 30, 20);
        CompoundTag root = rootWithTask(inside, Direction.EAST);
        ListTag list = root.getList("Tasks", 10);
        CompoundTag second = new CompoundTag();
        second.putLong("Target", outside.asLong());
        second.putByte("Facing", (byte) Direction.WEST.get3DDataValue());
        list.add(second);

        ExcavationLadderTaskData data = ExcavationLadderTaskData.load(root);
        assertEquals(1, data.cancelIntersecting(new BlockPos(9, 29, 9), new BlockPos(11, 31, 11)));
        assertTrue(data.task(inside).isEmpty());
        assertEquals(Direction.WEST, data.task(outside).orElseThrow().facing());
    }

    private static CompoundTag rootWithTask(BlockPos target, Direction facing) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag task = new CompoundTag();
        task.putLong("Target", target.asLong());
        task.putByte("Facing", (byte) facing.get3DDataValue());
        list.add(task);
        root.put("Tasks", list);
        return root;
    }
}
