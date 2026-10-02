package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Runtime job registry for one ServerLevel.
 *
 * Producers such as designations, production and logistics publish immutable CitizenJob values here.
 * Workers reserve jobs before travelling to them, preventing multiple Citizens from performing the same
 * physical action. The board deliberately does not scan the world itself.
 */
public final class CitizenJobBoard {
    private static final long STALE_RESERVATION_TICKS = 20L * 15L;
    private static final Map<ServerLevel, CitizenJobBoard> BOARDS = new WeakHashMap<>();

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private long nextId = 1L;

    public static CitizenJobBoard forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        synchronized (BOARDS) {
            return BOARDS.computeIfAbsent(level, ignored -> new CitizenJobBoard());
        }
    }

    public long publish(WorkType workType, BlockPos target, long createdTick) {
        return publish(workType, target, null, 0, createdTick);
    }

    public long publish(WorkType workType, BlockPos target, @Nullable CitizenSkill requiredSkill,
                        int minimumSkill, long createdTick) {
        Objects.requireNonNull(workType, "workType");
        Objects.requireNonNull(target, "target");

        for (Entry entry : entries.values()) {
            CitizenJob existing = entry.job;
            if (existing.workType() == workType && existing.target().equals(target)) {
                return existing.id();
            }
        }

        long id = nextId++;
        CitizenJob job = requiredSkill == null
                ? CitizenJob.simple(id, workType, target, createdTick)
                : CitizenJob.requiring(id, workType, target, requiredSkill, minimumSkill, createdTick);
        entries.put(id, new Entry(job));
        return id;
    }

    public Collection<CitizenJob> availableJobs(UUID workerId, long gameTime) {
        clearStaleReservations(gameTime);
        ArrayList<CitizenJob> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.reservedBy == null || entry.reservedBy.equals(workerId)) {
                result.add(entry.job);
            }
        }
        return List.copyOf(result);
    }

    public Optional<CitizenJob> job(long jobId) {
        Entry entry = entries.get(jobId);
        return entry == null ? Optional.empty() : Optional.of(entry.job);
    }

    public boolean reserve(long jobId, UUID workerId, long gameTime) {
        Entry entry = entries.get(jobId);
        if (entry == null || workerId == null) {
            return false;
        }
        if (entry.reservedBy != null && !entry.reservedBy.equals(workerId)
                && gameTime - entry.reservedTick <= STALE_RESERVATION_TICKS) {
            return false;
        }
        entry.reservedBy = workerId;
        entry.reservedTick = gameTime;
        return true;
    }

    public void touch(long jobId, UUID workerId, long gameTime) {
        Entry entry = entries.get(jobId);
        if (entry != null && workerId != null && workerId.equals(entry.reservedBy)) {
            entry.reservedTick = gameTime;
        }
    }

    public void release(long jobId, UUID workerId) {
        Entry entry = entries.get(jobId);
        if (entry != null && workerId != null && workerId.equals(entry.reservedBy)) {
            entry.reservedBy = null;
            entry.reservedTick = 0L;
        }
    }

    public void complete(long jobId, UUID workerId) {
        Entry entry = entries.get(jobId);
        if (entry == null) {
            return;
        }
        if (entry.reservedBy == null || entry.reservedBy.equals(workerId)) {
            entries.remove(jobId);
        }
    }

    public void remove(long jobId) {
        entries.remove(jobId);
    }

    public int size() {
        return entries.size();
    }

    private void clearStaleReservations(long gameTime) {
        for (Entry entry : entries.values()) {
            if (entry.reservedBy != null && gameTime - entry.reservedTick > STALE_RESERVATION_TICKS) {
                entry.reservedBy = null;
                entry.reservedTick = 0L;
            }
        }
    }

    private static final class Entry {
        private final CitizenJob job;
        private UUID reservedBy;
        private long reservedTick;

        private Entry(CitizenJob job) {
            this.job = job;
        }
    }
}
