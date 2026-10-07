package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent job registry for one ServerLevel.
 *
 * Producers such as designations, production and logistics publish immutable CitizenJob values here.
 * Jobs survive world saves; worker reservations are deliberately runtime-only and are reset after reload.
 */
public final class CitizenJobBoard extends SavedData {
    private static final String DATA_NAME = "stonebanner_citizen_jobs";
    private static final String TAG_NEXT_ID = "NextId";
    private static final String TAG_JOBS = "Jobs";
    private static final String TAG_ID = "Id";
    private static final String TAG_WORK_TYPE = "WorkType";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_REQUIRED_SKILL = "RequiredSkill";
    private static final String TAG_MINIMUM_SKILL = "MinimumSkill";
    private static final String TAG_CREATED_TICK = "CreatedTick";
    private static final long STALE_RESERVATION_TICKS = 20L * 15L;

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private long nextId = 1L;

    public static CitizenJobBoard forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(
                CitizenJobBoard::load,
                CitizenJobBoard::new,
                DATA_NAME
        );
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
        setDirty();
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

    public Collection<CitizenJob> snapshot() {
        return entries.values().stream().map(entry -> entry.job).toList();
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

    public boolean touch(long jobId, UUID workerId, long gameTime) {
        Entry entry = entries.get(jobId);
        if (entry != null && workerId != null && workerId.equals(entry.reservedBy)) {
            entry.reservedTick = gameTime;
            return true;
        }
        return false;
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
            setDirty();
        }
    }

    public void remove(long jobId) {
        if (entries.remove(jobId) != null) {
            setDirty();
        }
    }

    public int removeAt(BlockPos target) {
        if (target == null) {
            return 0;
        }
        int before = entries.size();
        entries.entrySet().removeIf(entry -> entry.getValue().job.target().equals(target));
        int removed = before - entries.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putLong(TAG_NEXT_ID, nextId);
        ListTag jobs = new ListTag();
        for (Entry entry : entries.values()) {
            CitizenJob job = entry.job;
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_ID, job.id());
            tag.putString(TAG_WORK_TYPE, job.workType().serializedName());
            tag.putLong(TAG_TARGET, job.target().asLong());
            if (job.requiredSkill() != null) {
                tag.putString(TAG_REQUIRED_SKILL, job.requiredSkill().serializedName());
            }
            tag.putInt(TAG_MINIMUM_SKILL, job.minimumSkill());
            tag.putLong(TAG_CREATED_TICK, job.createdTick());
            jobs.add(tag);
        }
        root.put(TAG_JOBS, jobs);
        return root;
    }

    public static CitizenJobBoard load(CompoundTag root) {
        CitizenJobBoard board = new CitizenJobBoard();
        long highestId = 0L;
        ListTag jobs = root.getList(TAG_JOBS, Tag.TAG_COMPOUND);
        for (int index = 0; index < jobs.size(); index++) {
            CompoundTag tag = jobs.getCompound(index);
            WorkType workType = parseWorkType(tag.getString(TAG_WORK_TYPE));
            if (workType == null) {
                continue;
            }

            long id = tag.getLong(TAG_ID);
            BlockPos target = BlockPos.of(tag.getLong(TAG_TARGET));
            CitizenSkill requiredSkill = tag.contains(TAG_REQUIRED_SKILL, Tag.TAG_STRING)
                    ? parseSkill(tag.getString(TAG_REQUIRED_SKILL))
                    : null;
            int minimumSkill = tag.getInt(TAG_MINIMUM_SKILL);
            long createdTick = tag.getLong(TAG_CREATED_TICK);
            CitizenJob job = requiredSkill == null
                    ? CitizenJob.simple(id, workType, target, createdTick)
                    : CitizenJob.requiring(id, workType, target, requiredSkill, minimumSkill, createdTick);
            board.entries.put(id, new Entry(job));
            highestId = Math.max(highestId, id);
        }

        long savedNextId = root.contains(TAG_NEXT_ID, Tag.TAG_LONG) ? root.getLong(TAG_NEXT_ID) : 1L;
        board.nextId = Math.max(Math.max(1L, savedNextId), highestId + 1L);
        return board;
    }

    @Nullable
    private static WorkType parseWorkType(String value) {
        for (WorkType workType : WorkType.values()) {
            if (workType.serializedName().equalsIgnoreCase(value)) {
                return workType;
            }
        }
        return null;
    }

    @Nullable
    private static CitizenSkill parseSkill(String value) {
        for (CitizenSkill skill : CitizenSkill.values()) {
            if (skill.serializedName().equalsIgnoreCase(value)) {
                return skill;
            }
        }
        return null;
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
