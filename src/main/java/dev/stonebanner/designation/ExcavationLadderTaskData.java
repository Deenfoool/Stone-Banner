package dev.stonebanner.designation;

import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistent metadata for excavation support jobs that place real vanilla ladders. */
public final class ExcavationLadderTaskData extends SavedData {
    private static final String DATA_NAME = "stonebanner_excavation_ladder_tasks";
    private static final String TAG_TASKS = "Tasks";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_FACING = "Facing";

    private final Map<Long, LadderTask> tasks = new LinkedHashMap<>();

    public static ExcavationLadderTaskData forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(
                ExcavationLadderTaskData::load,
                ExcavationLadderTaskData::new,
                DATA_NAME
        );
    }

    public void publish(ServerLevel level, BlockPos target, Direction facing) {
        if (level == null || target == null || facing == null || !facing.getAxis().isHorizontal()) {
            return;
        }
        long key = target.asLong();
        LadderTask existing = tasks.get(key);
        if (existing == null || existing.facing() != facing) {
            tasks.put(key, new LadderTask(target, facing));
            setDirty();
        }
        CitizenJobBoard.forLevel(level).publish(
                WorkType.BUILDING,
                target,
                CitizenSkill.CONSTRUCTION,
                0,
                level.getGameTime()
        );
    }

    public Optional<LadderTask> task(BlockPos target) {
        if (target == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(target.asLong()));
    }

    public void complete(BlockPos target) {
        if (target != null && tasks.remove(target.asLong()) != null) {
            setDirty();
        }
    }

    public int cancelIntersecting(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return 0;
        }
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());

        int before = tasks.size();
        tasks.entrySet().removeIf(entry -> {
            BlockPos pos = BlockPos.of(entry.getKey());
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        });
        int removed = before - tasks.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (LadderTask task : tasks.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_TARGET, task.target().asLong());
            tag.putByte(TAG_FACING, (byte) task.facing().get3DDataValue());
            list.add(tag);
        }
        root.put(TAG_TASKS, list);
        return root;
    }

    static ExcavationLadderTaskData load(CompoundTag root) {
        ExcavationLadderTaskData data = new ExcavationLadderTaskData();
        ListTag list = root.getList(TAG_TASKS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            BlockPos target = BlockPos.of(tag.getLong(TAG_TARGET));
            Direction facing = Direction.from3DDataValue(tag.getByte(TAG_FACING));
            if (facing.getAxis().isHorizontal()) {
                data.tasks.put(target.asLong(), new LadderTask(target, facing));
            }
        }
        return data;
    }

    public record LadderTask(BlockPos target, Direction facing) {
        public LadderTask {
            target = target.immutable();
        }
    }
}
