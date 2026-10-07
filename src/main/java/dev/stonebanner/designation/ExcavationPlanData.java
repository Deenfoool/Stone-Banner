package dev.stonebanner.designation;

import dev.stonebanner.citizen.CitizenJob;
import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkTargetRules;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent sequencing for volume excavation.
 *
 * The job board answers "what work is currently available". This class answers
 * "which part of an excavation is allowed to become available". Quarries expose
 * horizontal layers top-down; tunnels expose cross-sections from their entrance forward.
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
    private static final String TAG_MODE = "Mode";
    private static final String TAG_ACCESS_MODE = "AccessMode";
    private static final String TAG_CURRENT_SLICE = "CurrentSlice";
    private static final String TAG_CURRENT_Y_LEGACY = "CurrentY";
    private static final String TAG_STEP = "Step";
    private static final long RECONCILE_INTERVAL_TICKS = 20L;
    private static final int LOCAL_RECONCILE_MARGIN = 8;
    private static final int MAX_SYNC_PLANS = 256;

    private final Map<Long, Plan> plans = new LinkedHashMap<>();
    private final LinkedHashSet<Long> dirtyPlanIds = new LinkedHashSet<>();
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

    /** Creates a top-down quarry plan and publishes only its first non-empty horizontal layer. */
    public int createVertical(ServerLevel level, BlockPos first, BlockPos second) {
        return createVertical(level, first, second, ExcavationAccessMode.AUTO);
    }

    /** Creates a top-down quarry plan using the selected persistent access strategy. */
    public int createVertical(ServerLevel level, BlockPos first, BlockPos second, ExcavationAccessMode accessMode) {
        return create(level, Plan.vertical(nextId++, first, second,
                accessMode == null ? ExcavationAccessMode.AUTO : accessMode));
    }

    /**
     * Creates a horizontal tunnel/shaft plan. The longest horizontal axis becomes the tunnel axis;
     * the endpoint nearest the player becomes the entrance and sections open away from it.
     */
    public int createTunnel(ServerLevel level, BlockPos first, BlockPos second, BlockPos entranceHint) {
        return create(level, Plan.tunnel(nextId++, first, second, entranceHint));
    }

    private int create(ServerLevel level, Plan plan) {
        int totalTargets = countTargets(level, plan);
        if (totalTargets <= 0) {
            return 0;
        }

        plans.put(plan.id, plan);
        exposeCurrentOrNextSlice(level, plan);
        setDirty();
        syncAll(level);
        return totalTargets;
    }

    /**
     * Reconciles plans at most once per second. Workers call this while looking for jobs,
     * so a cleared slice naturally exposes the next one without a global world scan.
     */
    public void reconcileIfDue(ServerLevel level) {
        reconcileDirty(level);
        long gameTime = level.getGameTime();
        if (lastReconcileTick != Long.MIN_VALUE
                && gameTime - lastReconcileTick < RECONCILE_INTERVAL_TICKS) {
            return;
        }
        lastReconcileTick = gameTime;

        boolean hadPlans = !plans.isEmpty();
        List<Long> ids = new ArrayList<>(plans.keySet());
        for (Long id : ids) {
            Plan plan = plans.get(id);
            if (plan != null) {
                reconcile(level, plan);
            }
        }
        if (hadPlans || !plans.isEmpty()) {
            syncAll(level);
        }
    }

    /**
     * Marks only plans whose work volume or immediate access area may be affected by a block change.
     * The actual world query is deferred until the end of the server tick, after break/place events
     * have finished mutating the level.
     */
    public int markWorldChanged(BlockPos changed) {
        if (changed == null || plans.isEmpty()) {
            return 0;
        }
        int marked = 0;
        for (Plan plan : plans.values()) {
            if (plan.isInsideLocalInfluence(changed, LOCAL_RECONCILE_MARGIN)
                    && dirtyPlanIds.add(plan.id)) {
                marked++;
            }
        }
        return marked;
    }

    /** Reconciles only locally invalidated plans and publishes one compact snapshot afterwards. */
    public int reconcileDirty(ServerLevel level) {
        if (level == null || dirtyPlanIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = List.copyOf(dirtyPlanIds);
        dirtyPlanIds.clear();
        int reconciled = 0;
        for (Long id : ids) {
            Plan plan = plans.get(id);
            if (plan != null) {
                reconcile(level, plan);
                reconciled++;
            }
        }
        if (reconciled > 0) {
            syncAll(level);
        }
        return reconciled;
    }

    int dirtyPlanCount() {
        return dirtyPlanIds.size();
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
            dirtyPlanIds.remove(plan.id);
        }
        setDirty();
        syncAll(level);
        return removedJobs + removedPlans.size();
    }

    public int activePlanCount() {
        return plans.size();
    }

    /** Compact plan metadata for UI/commands without exposing mutable persistence internals. */
    public List<PlanSummary> summaries() {
        ArrayList<PlanSummary> result = new ArrayList<>(plans.size());
        for (Plan plan : plans.values()) {
            result.add(new PlanSummary(
                    plan.id,
                    new BlockPos(plan.minX, plan.minY, plan.minZ),
                    new BlockPos(plan.maxX, plan.maxY, plan.maxZ),
                    plan.mode.serializedName(),
                    plan.accessMode,
                    plan.currentSlice,
                    plan.step
            ));
        }
        return List.copyOf(result);
    }

    /**
     * Extends only the deep end of an active horizontal tunnel while keeping its cross-section, entrance,
     * current front and direction intact. The final plan must still satisfy the normal designation limits.
     */
    public ExtensionResult extendTunnel(ServerLevel level, long planId, int additionalLength) {
        if (level == null || additionalLength <= 0) {
            return new ExtensionResult(ExtensionStatus.INVALID_LENGTH, 0);
        }
        Plan plan = plans.get(planId);
        if (plan == null) {
            return new ExtensionResult(ExtensionStatus.NOT_FOUND, 0);
        }
        if (plan.mode == Mode.VERTICAL) {
            return new ExtensionResult(ExtensionStatus.NOT_TUNNEL, 0);
        }

        Plan extended;
        try {
            extended = plan.extendedBy(additionalLength);
        } catch (ArithmeticException ignored) {
            return new ExtensionResult(ExtensionStatus.TOO_LARGE, 0);
        }

        BlockPos extendedMin = new BlockPos(extended.minX, extended.minY, extended.minZ);
        BlockPos extendedMax = new BlockPos(extended.maxX, extended.maxY, extended.maxZ);
        if (!DesignationLimits.isAllowed(extendedMin, extendedMax)) {
            return new ExtensionResult(ExtensionStatus.TOO_LARGE, 0);
        }

        int addedTargets = countAddedTargets(level, plan, extended);
        plans.put(planId, extended);
        setDirty();
        // The current front does not move. If it was waiting/reconciling, preserve the same exposed section.
        exposeCurrentOrNextSlice(level, extended);
        syncAll(level);
        return new ExtensionResult(ExtensionStatus.EXTENDED, addedTargets);
    }

    /** Whether a target belongs to an active designated volume, including its future slices. */
    public boolean containsActiveTarget(BlockPos target) {
        return target != null && plans.values().stream()
                .anyMatch(plan -> plan.currentSliceInsideBounds() && plan.contains(target));
    }

    /**
     * Exposes immutable geometry only for a target in the currently active slice.
     * Runtime safety code uses this instead of reaching into persistent plan internals.
     */
    Optional<PlanView> activePlanFor(BlockPos target) {
        if (target == null) {
            return Optional.empty();
        }
        for (Plan plan : plans.values()) {
            if (plan.currentSliceInsideBounds() && plan.isInCurrentSlice(target)) {
                return Optional.of(view(plan));
            }
        }
        return Optional.empty();
    }

    public int hazardPausedPlanCount(ServerLevel level) {
        int paused = 0;
        for (Plan plan : plans.values()) {
            if (plan.currentSliceInsideBounds() && sliceHasHazard(level, plan)) {
                paused++;
            }
        }
        return paused;
    }

    /** Sends the current compact overlay state to one player, including an empty snapshot. */
    public void syncTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StoneBannerNetwork.sendExcavationSnapshot(player, networkSnapshots(player.serverLevel()));
    }

    private void syncAll(ServerLevel level) {
        List<ExcavationPlanSnapshotPacket.PlanSnapshot> snapshot = networkSnapshots(level);
        for (ServerPlayer player : level.players()) {
            StoneBannerNetwork.sendExcavationSnapshot(player, snapshot);
        }
    }

    private List<ExcavationPlanSnapshotPacket.PlanSnapshot> networkSnapshots(ServerLevel level) {
        ArrayList<ExcavationPlanSnapshotPacket.PlanSnapshot> snapshot = new ArrayList<>();
        for (Plan plan : plans.values()) {
            if (snapshot.size() >= MAX_SYNC_PLANS) {
                break;
            }
            snapshot.add(new ExcavationPlanSnapshotPacket.PlanSnapshot(
                    plan.id,
                    new BlockPos(plan.minX, plan.minY, plan.minZ),
                    new BlockPos(plan.maxX, plan.maxY, plan.maxZ),
                    plan.mode.ordinal(),
                    plan.accessMode.ordinal(),
                    plan.currentSlice,
                    plan.step,
                    plan.currentSliceInsideBounds() && sliceHasHazard(level, plan)
            ));
        }
        return List.copyOf(snapshot);
    }

    private void reconcile(ServerLevel level, Plan plan) {
        if (!plan.currentSliceInsideBounds()) {
            plans.remove(plan.id);
            setDirty();
            return;
        }

        if (sliceHasHazard(level, plan)) {
            removeCurrentSliceJobs(level, plan);
            return;
        }

        if (sliceHasTargets(level, plan)) {
            if (!ExcavationEgressSafety.canExposeCurrentSlice(level, view(plan))) {
                removeCurrentSliceJobs(level, plan);
                return;
            }
            publishCurrentSlice(level, plan);
            return;
        }

        plan.advance();
        exposeCurrentOrNextSlice(level, plan);
        setDirty();
    }

    private void exposeCurrentOrNextSlice(ServerLevel level, Plan plan) {
        while (plan.currentSliceInsideBounds()) {
            if (sliceHasHazard(level, plan)) {
                removeCurrentSliceJobs(level, plan);
                return;
            }
            if (sliceHasTargets(level, plan)) {
                if (!ExcavationEgressSafety.canExposeCurrentSlice(level, view(plan))) {
                    removeCurrentSliceJobs(level, plan);
                    return;
                }
                publishCurrentSlice(level, plan);
                return;
            }
            plan.advance();
        }
        plans.remove(plan.id);
    }

    private static PlanView view(Plan plan) {
        return new PlanView(
                plan.minX,
                plan.minY,
                plan.minZ,
                plan.maxX,
                plan.maxY,
                plan.maxZ,
                plan.mode.ordinal(),
                plan.accessMode.ordinal(),
                plan.currentSlice,
                plan.step
        );
    }

    private static void publishCurrentSlice(ServerLevel level, Plan plan) {
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        long gameTime = level.getGameTime();
        forEachPositionInCurrentSlice(plan, pos -> {
            if (!isExcavationTarget(level, plan, pos)) {
                return;
            }
            board.publish(WorkType.MINING, pos, CitizenSkill.MINING, 0, gameTime);
        });
    }

    private static void removeCurrentSliceJobs(ServerLevel level, Plan plan) {
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        for (CitizenJob job : List.copyOf(board.snapshot())) {
            if (job.workType() == WorkType.MINING && plan.isInCurrentSlice(job.target())) {
                board.remove(job.id());
            }
        }
    }

    private static int countTargets(ServerLevel level, Plan plan) {
        int count = 0;
        for (int x = plan.minX; x <= plan.maxX; x++) {
            for (int y = plan.minY; y <= plan.maxY; y++) {
                for (int z = plan.minZ; z <= plan.maxZ; z++) {
                    if (isExcavationTarget(level, plan, new BlockPos(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countAddedTargets(ServerLevel level, Plan previous, Plan extended) {
        int count = 0;
        for (int x = extended.minX; x <= extended.maxX; x++) {
            for (int y = extended.minY; y <= extended.maxY; y++) {
                for (int z = extended.minZ; z <= extended.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!previous.contains(pos) && isExcavationTarget(level, extended, pos)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean sliceHasTargets(ServerLevel level, Plan plan) {
        final boolean[] found = {false};
        forEachPositionInCurrentSlice(plan, pos -> {
            if (!found[0] && isExcavationTarget(level, plan, pos)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean sliceHasHazard(ServerLevel level, Plan plan) {
        final boolean[] found = {false};
        forEachPositionInCurrentSlice(plan, pos -> {
            if (found[0] || !level.hasChunkAt(pos)) {
                return;
            }
            if (level.getFluidState(pos).is(FluidTags.WATER)
                    || level.getFluidState(pos).is(FluidTags.LAVA)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean isExcavationTarget(ServerLevel level, Plan plan, BlockPos pos) {
        return level.hasChunkAt(pos)
                && !plan.isReservedRampSupport(pos)
                && WorkTargetRules.isValid(WorkType.MINING, level, pos);
    }

    private static void forEachPositionInCurrentSlice(Plan plan, PositionConsumer consumer) {
        switch (plan.mode) {
            case VERTICAL -> {
                int y = plan.currentSlice;
                for (int x = plan.minX; x <= plan.maxX; x++) {
                    for (int z = plan.minZ; z <= plan.maxZ; z++) {
                        consumer.accept(new BlockPos(x, y, z));
                    }
                }
            }
            case TUNNEL_X -> {
                int x = plan.currentSlice;
                for (int y = plan.minY; y <= plan.maxY; y++) {
                    for (int z = plan.minZ; z <= plan.maxZ; z++) {
                        consumer.accept(new BlockPos(x, y, z));
                    }
                }
            }
            case TUNNEL_Z -> {
                int z = plan.currentSlice;
                for (int x = plan.minX; x <= plan.maxX; x++) {
                    for (int y = plan.minY; y <= plan.maxY; y++) {
                        consumer.accept(new BlockPos(x, y, z));
                    }
                }
            }
        }
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
            tag.putString(TAG_MODE, plan.mode.serializedName());
            tag.putString(TAG_ACCESS_MODE, plan.accessMode.serializedName());
            tag.putInt(TAG_CURRENT_SLICE, plan.currentSlice);
            tag.putInt(TAG_STEP, plan.step);
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
            Mode mode = tag.contains(TAG_MODE, Tag.TAG_STRING)
                    ? Mode.fromSerializedName(tag.getString(TAG_MODE))
                    : Mode.VERTICAL;
            ExcavationAccessMode accessMode = tag.contains(TAG_ACCESS_MODE, Tag.TAG_STRING)
                    ? ExcavationAccessMode.fromSerializedName(tag.getString(TAG_ACCESS_MODE))
                    : ExcavationAccessMode.AUTO;
            int currentSlice = tag.contains(TAG_CURRENT_SLICE, Tag.TAG_INT)
                    ? tag.getInt(TAG_CURRENT_SLICE)
                    : tag.getInt(TAG_CURRENT_Y_LEGACY);
            int step = tag.contains(TAG_STEP, Tag.TAG_INT)
                    ? normalizeStep(tag.getInt(TAG_STEP))
                    : -1;
            Plan plan = new Plan(
                    tag.getLong(TAG_ID),
                    tag.getInt(TAG_MIN_X),
                    tag.getInt(TAG_MIN_Y),
                    tag.getInt(TAG_MIN_Z),
                    tag.getInt(TAG_MAX_X),
                    tag.getInt(TAG_MAX_Y),
                    tag.getInt(TAG_MAX_Z),
                    mode,
                    accessMode,
                    currentSlice,
                    step
            );
            data.plans.put(plan.id, plan);
            highestId = Math.max(highestId, plan.id);
        }
        long savedNext = root.contains(TAG_NEXT_ID, Tag.TAG_LONG) ? root.getLong(TAG_NEXT_ID) : 1L;
        data.nextId = Math.max(Math.max(1L, savedNext), highestId + 1L);
        return data;
    }

    private static int normalizeStep(int value) {
        return value < 0 ? -1 : 1;
    }

    private enum Mode {
        VERTICAL,
        TUNNEL_X,
        TUNNEL_Z;

        private String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static Mode fromSerializedName(String value) {
            for (Mode mode : values()) {
                if (mode.serializedName().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return VERTICAL;
        }
    }

    private static final class Plan {
        private final long id;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final Mode mode;
        private final ExcavationAccessMode accessMode;
        private int currentSlice;
        private final int step;

        private Plan(long id, int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ,
                     Mode mode, ExcavationAccessMode accessMode, int currentSlice, int step) {
            this.id = id;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.mode = mode;
            this.accessMode = accessMode == null ? ExcavationAccessMode.AUTO : accessMode;
            this.currentSlice = currentSlice;
            this.step = normalizeStep(step);
        }

        private static Plan vertical(long id, BlockPos first, BlockPos second, ExcavationAccessMode accessMode) {
            Bounds bounds = Bounds.from(first, second);
            return new Plan(id, bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    Mode.VERTICAL, accessMode, bounds.maxY, -1);
        }

        private static Plan tunnel(long id, BlockPos first, BlockPos second, BlockPos entranceHint) {
            Bounds bounds = Bounds.from(first, second);
            int spanX = bounds.maxX - bounds.minX;
            int spanZ = bounds.maxZ - bounds.minZ;
            BlockPos hint = entranceHint == null ? first : entranceHint;

            if (spanX >= spanZ) {
                boolean enterFromMin = Math.abs(hint.getX() - bounds.minX)
                        <= Math.abs(hint.getX() - bounds.maxX);
                return new Plan(id, bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ,
                        Mode.TUNNEL_X, ExcavationAccessMode.AUTO,
                        enterFromMin ? bounds.minX : bounds.maxX,
                        enterFromMin ? 1 : -1);
            }

            boolean enterFromMin = Math.abs(hint.getZ() - bounds.minZ)
                    <= Math.abs(hint.getZ() - bounds.maxZ);
            return new Plan(id, bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    Mode.TUNNEL_Z, ExcavationAccessMode.AUTO,
                    enterFromMin ? bounds.minZ : bounds.maxZ,
                    enterFromMin ? 1 : -1);
        }

        private Plan extendedBy(int length) {
            if (mode == Mode.VERTICAL || length <= 0) {
                return this;
            }
            if (mode == Mode.TUNNEL_X) {
                if (step > 0) {
                    return new Plan(id, minX, minY, minZ, Math.addExact(maxX, length), maxY, maxZ,
                            mode, accessMode, currentSlice, step);
                }
                return new Plan(id, Math.subtractExact(minX, length), minY, minZ, maxX, maxY, maxZ,
                        mode, accessMode, currentSlice, step);
            }
            if (step > 0) {
                return new Plan(id, minX, minY, minZ, maxX, maxY, Math.addExact(maxZ, length),
                        mode, accessMode, currentSlice, step);
            }
            return new Plan(id, minX, minY, Math.subtractExact(minZ, length), maxX, maxY, maxZ,
                    mode, accessMode, currentSlice, step);
        }

        private void advance() {
            currentSlice += step;
        }

        private boolean currentSliceInsideBounds() {
            return switch (mode) {
                case VERTICAL -> currentSlice >= minY && currentSlice <= maxY;
                case TUNNEL_X -> currentSlice >= minX && currentSlice <= maxX;
                case TUNNEL_Z -> currentSlice >= minZ && currentSlice <= maxZ;
            };
        }

        private boolean isInCurrentSlice(BlockPos pos) {
            if (!contains(pos)) {
                return false;
            }
            return switch (mode) {
                case VERTICAL -> pos.getY() == currentSlice;
                case TUNNEL_X -> pos.getX() == currentSlice;
                case TUNNEL_Z -> pos.getZ() == currentSlice;
            };
        }

        private boolean usesLadderAccess() {
            if (mode != Mode.VERTICAL) {
                return false;
            }
            if (accessMode == ExcavationAccessMode.LADDERS) {
                return true;
            }
            if (accessMode == ExcavationAccessMode.RAMP) {
                return false;
            }
            return maxX <= minX || maxZ <= minZ;
        }

        /** Preserve one block per depth around the perimeter only when the selected strategy uses a ramp. */
        private boolean isReservedRampSupport(BlockPos pos) {
            if (mode != Mode.VERTICAL || usesLadderAccess() || maxX <= minX || maxZ <= minZ) {
                return false;
            }
            if (pos.getY() < minY || pos.getY() > maxY) {
                return false;
            }
            BlockPos support = rampSupportAtY(pos.getY());
            return support != null && support.equals(pos);
        }

        private BlockPos rampSupportAtY(int y) {
            int sizeX = maxX - minX + 1;
            int sizeZ = maxZ - minZ + 1;
            if (sizeX < 2 || sizeZ < 2) {
                return null;
            }

            int perimeter = 2 * sizeX + 2 * sizeZ - 4;
            int depth = maxY - y;
            int index = Math.floorMod(depth, perimeter);

            if (index < sizeX) {
                return new BlockPos(minX + index, y, minZ);
            }
            index -= sizeX;
            if (index < sizeZ - 1) {
                return new BlockPos(maxX, y, minZ + 1 + index);
            }
            index -= sizeZ - 1;
            if (index < sizeX - 1) {
                return new BlockPos(maxX - 1 - index, y, maxZ);
            }
            index -= sizeX - 1;
            return new BlockPos(minX, y, maxZ - 1 - index);
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

        private boolean isInsideLocalInfluence(BlockPos pos, int margin) {
            return (long) pos.getX() >= (long) minX - margin && (long) pos.getX() <= (long) maxX + margin
                    && (long) pos.getY() >= (long) minY - margin && (long) pos.getY() <= (long) maxY + margin
                    && (long) pos.getZ() >= (long) minZ - margin && (long) pos.getZ() <= (long) maxZ + margin;
        }
    }

    public record PlanSummary(long id, BlockPos min, BlockPos max, String mode,
                              ExcavationAccessMode accessMode, int currentSlice, int step) {
        public PlanSummary {
            min = min.immutable();
            max = max.immutable();
            accessMode = accessMode == null ? ExcavationAccessMode.AUTO : accessMode;
        }
    }

    public record ExtensionResult(ExtensionStatus status, int addedTargets) {
    }

    public enum ExtensionStatus {
        EXTENDED,
        NOT_FOUND,
        NOT_TUNNEL,
        INVALID_LENGTH,
        TOO_LARGE
    }

    record PlanView(int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ,
                    int modeCode, int accessModeCode, int currentSlice, int step) {
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

    @FunctionalInterface
    private interface PositionConsumer {
        void accept(BlockPos pos);
    }
}
