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
    void legacySaveWithoutNextIdDoesNotReuseExistingJobIdentity() throws java.io.IOException {
        CitizenJobBoard board = new CitizenJobBoard();
        long first = board.publish(WorkType.MINING, new BlockPos(1, 40, 1), 5);
        long surviving = board.publish(WorkType.FORESTRY, new BlockPos(2, 70, 2), 6);
        UUID worker = UUID.randomUUID();
        assertTrue(board.reserve(first, worker, 7));
        board.complete(first, worker);
        CompoundTag saved = board.save(new CompoundTag());
        saved.remove("NextId");
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.writeCompressed(saved, bytes);
        CitizenJobBoard loaded = CitizenJobBoard.load(net.minecraft.nbt.NbtIo.readCompressed(
                new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(1, loaded.size());
        assertFalse(loaded.job(first).isPresent(), "Completed work must not reappear after reload");
        assertEquals(board.job(surviving), loaded.job(surviving));
        long next = loaded.publish(WorkType.MINING, new BlockPos(3, 40, 3), 8);
        assertTrue(next > surviving, "Legacy reload reused a persistent job identity");
    }

    @Test
    void reloadedReservationBelongsOnlyToNewClaimant() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.FORESTRY, BlockPos.ZERO, 0);
        UUID previousWorker = UUID.randomUUID();
        UUID newWorker = UUID.randomUUID();
        assertTrue(board.reserve(id, previousWorker, 1));
        CitizenJobBoard loaded = CitizenJobBoard.load(board.save(new CompoundTag()));
        assertFalse(loaded.touch(id, previousWorker, 2), "Reload resurrected the old lease");
        assertTrue(loaded.reserve(id, newWorker, 2));
        loaded.complete(id, previousWorker);
        loaded.release(id, previousWorker);
        assertTrue(loaded.job(id).isPresent(), "Old worker completed newly reassigned work");
        assertTrue(loaded.touch(id, newWorker, 3));
        loaded.complete(id, newWorker);
        assertFalse(loaded.job(id).isPresent());
    }


    @Test
    void aRewoundWorldClockLetsAnotherWorkerReclaimJobWithoutQuery() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.FORESTRY, new BlockPos(4, 70, 8), 900);
        UUID original = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(board.reserve(id, original, 1_200));
        assertTrue(board.reserve(id, replacement, 200),
                "An old reservation must not remain valid after world time is rewound");
        assertFalse(board.touch(id, original, 201));
        board.release(id, original);
        board.complete(id, original);
        assertTrue(board.job(id).isPresent(), "Old owner must not finish the new owner's work");
        assertTrue(board.touch(id, replacement, 202));
        board.complete(id, replacement);
        assertFalse(board.job(id).isPresent());
    }

    @Test
    void availableJobsClearsReservationsFromAFutureTimeline() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.MINING, BlockPos.ZERO, 5);
        UUID previous = UUID.randomUUID();
        UUID worker = UUID.randomUUID();

        assertTrue(board.reserve(id, previous, 5_000));
        assertEquals(1, board.availableJobs(worker, 100).size(),
                "The job must be visible after loading an earlier world save");
        assertTrue(board.reserve(id, worker, 100));
        assertFalse(board.touch(id, previous, 101));
    }

    @Test
    void validReservationSurvivesUntilTimeoutThenExpires() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.MINING, BlockPos.ZERO, 1);
        UUID owner = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(board.reserve(id, owner, 10));
        assertTrue(board.availableJobs(second, 310).isEmpty(),
                "The reservation is valid on the inclusive 300-tick boundary");
        assertFalse(board.reserve(id, second, 310));
        assertEquals(1, board.availableJobs(second, 311).size());
        assertTrue(board.reserve(id, second, 311));
        assertFalse(board.touch(id, owner, 312));
    }

    @Test
    void originalWorkerCanRenewItsLeaseAfterClockRollback() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.FORESTRY, BlockPos.ZERO, 1);
        UUID original = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertTrue(board.reserve(id, original, 9_000));
        assertTrue(board.touch(id, original, 20),
                "A worker already executing this job may renew the lease after the rewind");
        assertTrue(board.availableJobs(other, 21).isEmpty());
        assertFalse(board.reserve(id, other, 320));
        assertTrue(board.reserve(id, other, 321));
    }

    @Test
    void heartbeatCannotStealReassignedReservation() {
        CitizenJobBoard board = new CitizenJobBoard();
        long id = board.publish(WorkType.MINING, BlockPos.ZERO, 0);
        UUID oldWorker = UUID.randomUUID();
        UUID newWorker = UUID.randomUUID();
        assertTrue(board.reserve(id, oldWorker, 0));
        assertTrue(board.touch(id, oldWorker, 10));
        assertTrue(board.reserve(id, newWorker, 311));
        assertFalse(board.touch(id, oldWorker, 312));
        board.release(id, oldWorker);
        assertTrue(board.touch(id, newWorker, 313));
        assertFalse(board.touch(id + 1, newWorker, 313));
    }

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
