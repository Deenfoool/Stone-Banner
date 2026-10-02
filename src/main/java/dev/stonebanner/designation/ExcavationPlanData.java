package dev.stonebanner.designation;

import dev.stonebanner.citizen.CitizenJob;
import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkTargetRules;
import dev.stonebanner.citizen.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent sequencing for volume excavation.
 *
 * The job board answers "what work is currently available". This class answers
 * "which part of an excavation is allowed to become available". The first mode
 * is a vertical quarry that exposes one horizontal layer at a time, top-down.
 */
public final class ExcavationPlanData extends SavedData {
    private static final String DATA_NAME = "stonebanner_excavation_plans";
    private static final String TAG_NEXT_ID = "NextId";
    private static final String TAG_PLANS = "Plans";
    private static final String TAG_ID = "Id";
    private static final String TAG_MIN_X = "MinX";
    private static final String TAG_MIN_Y = "MinY";
    private static final String TAG_MIN_Z = "MinZ";
    private static final String TAG_MAX_X = "MaxX";
    private static final String TAG_MAX_Y = "MaxY";
    private static final String TAG_MAX_Z = "MaxZ";
    private static final String TAG_CURRENT_Y = "CurrentY";
    private static final long RECONCILE_INTERVAL_TICKS = 20L;

    private final Map<Long, Plan> plans = new LinkedHashMap<>();
    private long nextId = 1L;
    private long lastReconcileTick = Long.MIN_VALUE;

    public static ExcavationPlanData forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(
                ExcavationPlanData::load,
                ExcavationPlanData::new,
                DATA_NAME
        );
    }

    /** Creates a top-down quarry plan and publishes only its first non-empty layer. */
    public int createVertical(ServerLevel level, BlockPos first, BlockPos second) {
        Plan plan = Plan.from(nextId++, first, second);
        int totalTargets = countTargets(level, plan);
        if (totalTargets <= 0) {
            return 0;
        }

        plans.put(plan.id, plan);
        exposeCurrentOrNextLayer(level, plan);
        setDirty();
        return totalTargets;
    }

    /**
     * Reconciles plans at most once per second. Workers call this while looking for jobs,
     * so a cleared layer naturally exposes the next layer without a global world scan.
     */
    public void reconcileIfDue(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (lastReconcileTick != Long.MIN_VALUE
                && gameTime - lastReconcileTick < RECONCILE_INTERVAL_TICKS) {
            return;
        }
        lastReconcileTick = gameTime;

        List<Long> ids = new ArrayList<>(plans.keySet());
        for (Long id : ids) {
            Plan plan = plans.get(id);
            if (plan != null) {
                reconcile(level, plan);
            }
        }
    }

    /** Cancels every excavation intersecting the selected box and removes its active jobs. */
    public int cancelIntersecting(ServerLevel level, BlockPos first, BlockPos second) {
        Bounds selection = Bounds.from(first, second);
        List<Plan> removedPlans = plans.values().stream()
                .filter(plan -> plan.intersects(selection))
                .toList();
        if (removedPlans.isEmpty()) {
            return 0;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        int removedJobs = 0;
        for (CitizenJob job : List.copyOf(board.snapshot())) {
            if (job.workType() != WorkType.MINING) {
                continue;
            }
            for (Plan plan : removedPlans) {
                if (plan.contains(job.target())) {
                    board.remove(job.id());
                    removedJobs++;
                    break;
                }
            }
        }

        for (Plan plan : removedPlans) {
            plans.remove(plan.id);
        }
        setDirty();
        return removedJobs + removedPlans.size();
    }

    public int activePlanCount() {
        return plans.size();
    }

    private void reconcile(ServerLevel level, Plan plan) {
        if (plan.currentY < plan.minY) {
            plans.remove(plan.id);
            setDirty();
            return;
        }

        if (layerHasTargets(level, plan, plan.currentY)) {
            // Re-publish missing jobs if the world still contains a target block.
            publishLayer(level, plan, plan.currentY);
            return;
        }

        plan.currentY--;
        exposeCurrentOrNextLayer(level, plan);
        setDirty();
    }

    private void exposeCurrentOrNextLayer(ServerLevel level, Plan plan) {
        while (plan.currentY >= plan.minY) {
            if (layerHasTargets(level, plan, plan.currentY)) {
                publishLayer(level, plan, plan.currentY);
                return;
            }
            plan.currentY--;
        }
        plans.remove(plan.id);
    }

    private static void publishLayer(ServerLevel level, Plan plan, int y) {
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        long gameTime = level.getGameTime();
        for (int x = plan.minX; x <= plan.maxX; x++) {
            for (int z = plan.minZ; z <= plan.maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.hasChunkAt(pos) || !WorkTargetRules.isValid(WorkType.MINING, level, pos)) {
                    continue;
                }
                board.publish(WorkType.MINING, pos, CitizenSkill.MINING, 0, gameTime);
            }
        }
    }

    private static int countTargets(ServerLevel level, Plan plan) {
        int count = 0;
        for (int x = plan.minX; x <= plan.maxX; x++) {
            for (int y = plan.minY; y <= plan.maxY; y++) {
                for (int z = plan.minZ; z <= plan.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.hasChunkAt(pos) && WorkTargetRules.isValid(WorkType.MINING, level, pos)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean layerHasTargets(ServerLevel level, Plan plan, int y) {
        for (int x = plan.minX; x <= plan.maxX; x++) {
            for (int z = plan.minZ; z <= plan.maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.hasChunkAt(pos) && WorkTargetRules.isValid(WorkType.MINING, level, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putLong(TAG_NEXT_ID, nextId);
        ListTag list = new ListTag();
        for (Plan plan : plans.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_ID, plan.id);
            tag.putInt(TAG_MIN_X, plan.minX);
            tag.putInt(TAG_MIN_Y, plan.minY);
            tag.putInt(TAG_MIN_Z, plan.minZ);
            tag.putInt(TAG_MAX_X, plan.maxX);
            tag.putInt(TAG_MAX_Y, plan.maxY);
            tag.putInt(TAG_MAX_Z, plan.maxZ);
            tag.putInt(TAG_CURRENT_Y, plan.currentY);
            list.add(tag);
        }
        root.put(TAG_PLANS, list);
        return root;
    }

    public static ExcavationPlanData load(CompoundTag root) {
        ExcavationPlanData data = new ExcavationPlanData();
        long highestId = 0L;
        ListTag list = root.getList(TAG_PLANS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            Plan plan = new Plan(
                    tag.getLong(TAG_ID),
                    tag.getInt(TAG_MIN_X),
                    tag.getInt(TAG_MIN_Y),
                    tag.getInt(TAG_MIN_Z),
                    tag.getInt(TAG_MAX_X),
                    tag.getInt(TAG_MAX_Y),
                    tag.getInt(TAG_MAX_Z),
                    tag.getInt(TAG_CURRENT_Y)
            );
            data.plans.put(plan.id, plan);
            highestId = Math.max(highestId, plan.id);
        }
        long savedNext = root.contains(TAG_NEXT_ID, Tag.TAG_LONG) ? root.getLong(TAG_NEXT_ID) : 1L;
        data.nextId = Math.max(Math.max(1L, savedNext), highestId + 1L);
        return data;
    }

    private static final class Plan {
        private final long id;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private int currentY;

        private Plan(long id, int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ, int currentY) {
            this.id = id;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.currentY = currentY;
        }

        private static Plan from(long id, BlockPos first, BlockPos second) {
            Bounds bounds = Bounds.from(first, second);
            return new Plan(id, bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ, bounds.maxY);
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        private boolean intersects(Bounds other) {
            return maxX >= other.minX && minX <= other.maxX
                    && maxY >= other.minY && minY <= other.maxY
                    && maxZ >= other.minZ && minZ <= other.maxZ;
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(BlockPos first, BlockPos second) {
            return new Bounds(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ())
            );
        }
    }
}
