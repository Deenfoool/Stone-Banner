package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenJobBoardTest {
    @Test
    void deduplicatesSameWorkAtSameBlock() {
        CitizenJobBoard board = new CitizenJobBoard();
        BlockPos target = new BlockPos(4, 70, -2);

        long first = board.publish(WorkType.FORESTRY, target, 10L);
        long second = board.publish(WorkType.FORESTRY, target, 20L);

        assertEquals(first, second);
        assertEquals(1, board.size());
    }

    @Test
    void reservationHidesJobFromOtherWorker() {
        CitizenJobBoard board = new CitizenJobBoard();
        long jobId = board.publish(WorkType.FORESTRY, BlockPos.ZERO, 0L);
        UUID firstWorker = UUID.randomUUID();
        UUID secondWorker = UUID.randomUUID();

        assertTrue(board.reserve(jobId, firstWorker, 1L));
        assertEquals(1, board.availableJobs(firstWorker, 2L).size());
        assertTrue(board.availableJobs(secondWorker, 2L).isEmpty());
    }

    @Test
    void completingReservedJobRemovesIt() {
        CitizenJobBoard board = new CitizenJobBoard();
        long jobId = board.publish(WorkType.FORESTRY, BlockPos.ZERO, 0L);
        UUID worker = UUID.randomUUID();

        assertTrue(board.reserve(jobId, worker, 1L));
        board.complete(jobId, worker);

        assertFalse(board.job(jobId).isPresent());
        assertEquals(0, board.size());
    }

    @Test
    void cancellingTargetRemovesAllJobsAtThatBlock() {
        CitizenJobBoard board = new CitizenJobBoard();
        BlockPos target = new BlockPos(2, 64, 2);
        board.publish(WorkType.FORESTRY, target, 0L);
        board.publish(WorkType.MINING, target, CitizenSkill.MINING, 0, 1L);

        assertEquals(2, board.removeAt(target));
        assertEquals(0, board.size());
    }

    @Test
    void savedDataRoundTripsJobsButResetsReservations() {
        CitizenJobBoard board = new CitizenJobBoard();
        long forestry = board.publish(WorkType.FORESTRY, new BlockPos(1, 70, 1), 12L);
        long mining = board.publish(
                WorkType.MINING,
                new BlockPos(3, 42, -7),
                CitizenSkill.MINING,
                2,
                20L
        );
        UUID worker = UUID.randomUUID();
        assertTrue(board.reserve(mining, worker, 25L));

        CompoundTag saved = board.save(new CompoundTag());
        CitizenJobBoard loaded = CitizenJobBoard.load(saved);

        assertEquals(2, loaded.size());
        assertEquals(WorkType.FORESTRY, loaded.job(forestry).orElseThrow().workType());
        CitizenJob loadedMining = loaded.job(mining).orElseThrow();
        assertEquals(CitizenSkill.MINING, loadedMining.requiredSkill());
        assertEquals(2, loadedMining.minimumSkill());
        assertEquals(2, loaded.availableJobs(UUID.randomUUID(), 26L).size());

        long next = loaded.publish(WorkType.FORESTRY, new BlockPos(9, 70, 9), 30L);
        assertTrue(next > mining);
    }
}
