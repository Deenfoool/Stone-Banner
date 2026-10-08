package dev.stonebanner.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Persistent registry of real Minecraft containers used as Stone & Banner storage.
 *
 * <p>The registry stores positions and management authority. Item counts come from live container slots, so storage
 * never becomes a second virtual inventory or a global resource number.</p>
 */
public final class StorageData extends SavedData {
    private static final String DATA_NAME = "stonebanner_storage";
    private static final String TAG_CONTAINERS = "Containers";
    private static final int MAX_REGISTERED_CONTAINERS = 4096;

    private final LinkedHashSet<Long> containerPositions = new LinkedHashSet<>();
    // Management authority only. Existing storage remains a shared physical NPC supply network.
    private final Map<Long, UUID> managers = new HashMap<>();

    public static StorageData forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(StorageData::load, StorageData::new, DATA_NAME);
    }

    public RegisterResult register(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos) || liveContainer(level, pos).isEmpty()) {
            return RegisterResult.NOT_A_CONTAINER;
        }
        if (containerPositions.contains(pos.asLong())) {
            return RegisterResult.ALREADY_REGISTERED;
        }
        if (containerPositions.size() >= MAX_REGISTERED_CONTAINERS) {
            return RegisterResult.LIMIT_REACHED;
        }
        containerPositions.add(pos.asLong());
        setDirty();
        return RegisterResult.ADDED;
    }

    public boolean unregister(BlockPos pos) {
        if (pos == null || !containerPositions.remove(pos.asLong())) {
            return false;
        }
        managers.remove(pos.asLong());
        setDirty();
        return true;
    }

    public boolean isRegistered(BlockPos pos) { return pos != null && containerPositions.contains(pos.asLong()); }
    public Optional<UUID> manager(BlockPos pos) { return pos == null ? Optional.empty() : Optional.ofNullable(managers.get(pos.asLong())); }

    /** Old administrative registrations have no manager and require operator authority to change. */
    public boolean canManage(Collection<BlockPos> positions, UUID player, boolean operator) {
        return player != null && positions != null && positions.stream().allMatch(pos -> pos != null
                && (!isRegistered(pos) || operator || player.equals(managers.get(pos.asLong()))));
    }

    /** Validate the whole physical container group before adding either half of a double chest. */
    public RegisterResult registerManaged(ServerLevel level, Collection<BlockPos> positions, UUID player, boolean operator) {
        if (level == null || positions == null || positions.isEmpty() || positions.size() > 2 || player == null
                || positions.stream().anyMatch(pos -> pos == null || !level.hasChunkAt(pos) || liveContainer(level,pos).isEmpty()))
            return RegisterResult.NOT_A_CONTAINER;
        var distinct = positions.stream().distinct().toList();
        if (!canManage(distinct, player, operator)) return RegisterResult.FORBIDDEN;
        long additions = distinct.stream().filter(pos -> !isRegistered(pos)).count();
        if (containerPositions.size() + additions > MAX_REGISTERED_CONTAINERS) return RegisterResult.LIMIT_REACHED;
        if (additions == 0) return RegisterResult.ALREADY_REGISTERED;
        for (var pos : distinct) if (containerPositions.add(pos.asLong())) managers.put(pos.asLong(), player);
        setDirty();return RegisterResult.ADDED;
    }

    public boolean unregisterManaged(Collection<BlockPos> positions, UUID player, boolean operator) {
        if (positions == null || positions.isEmpty() || positions.size() > 2 || !canManage(positions,player,operator)) return false;
        for (var pos : positions) unregister(pos);
        return true;
    }

    public int registeredCount() {
        return containerPositions.size();
    }

    public Set<BlockPos> registeredPositions() {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        for (long packed : containerPositions) {
            positions.add(BlockPos.of(packed));
        }
        return Set.copyOf(positions);
    }

    /** Count matching real items in currently loaded, still-valid registered containers. */
    public int count(ServerLevel level, Predicate<ItemStack> predicate) {
        if (level == null || predicate == null) {
            return 0;
        }
        int total = 0;
        for (BlockPos pos : validLoadedPositions(level, null)) {
            Container container = liveContainer(level, pos).orElse(null);
            if (container == null) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    public int countItem(ServerLevel level, Item item) {
        return item == null ? 0 : count(level, stack -> stack.is(item));
    }

    /** Finds the nearest loaded registered container holding at least one matching real item. */
    public Optional<BlockPos> nearestContainerWithItem(ServerLevel level, BlockPos origin,
                                                       Predicate<ItemStack> predicate, double maxDistance) {
        if (level == null || origin == null || predicate == null) {
            return Optional.empty();
        }
        double maxDistanceSqr = maxDistance < 0.0D ? Double.POSITIVE_INFINITY : maxDistance * maxDistance;
        for (BlockPos pos : validLoadedPositions(level, origin)) {
            if (distanceSquared(origin, pos) > maxDistanceSqr) {
                continue;
            }
            Container container = liveContainer(level, pos).orElse(null);
            if (container == null) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    return Optional.of(pos.immutable());
                }
            }
        }
        return Optional.empty();
    }

    /** Loaded, nearest-first containers holding matching items; query does not remove anything. */
    public List<BlockPos> containersWithItem(ServerLevel level, BlockPos origin,
                                             Predicate<ItemStack> predicate, double radius) {
        if (level == null || origin == null || predicate == null) return List.of();
        double maxDistanceSqr = radius < 0 ? Double.POSITIVE_INFINITY : radius * radius;
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : validLoadedPositions(level, origin)) {
            if (distanceSquared(origin, pos) > maxDistanceSqr) continue;
            Container container = liveContainer(level, pos).orElse(null);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && predicate.test(stack)) { result.add(pos); break; }
            }
        }
        return List.copyOf(result);
    }

    /** Loaded, nearest-first candidates accepting any carried stack; query does not move items. */
    public List<BlockPos> acceptingContainers(ServerLevel level, BlockPos origin,
                                             List<ItemStack> offered, double maxDistance) {
        if (level == null || origin == null || offered == null || offered.isEmpty()) return List.of();
        double maxDistanceSqr = maxDistance < 0 ? Double.POSITIVE_INFINITY : maxDistance * maxDistance;
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : validLoadedPositions(level, origin)) {
            if (distanceSquared(origin, pos) > maxDistanceSqr) continue;
            Container container = liveContainer(level, pos).orElse(null);
            if (container != null && canAcceptAny(container, offered)) {
                result.add(pos.immutable());
            }
        }
        return List.copyOf(result);
    }

    /** Checks one registered loaded container without moving items. */
    public boolean canAcceptAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null || stack == null || stack.isEmpty()
                || !containerPositions.contains(pos.asLong()) || !level.hasChunkAt(pos)) return false;
        return liveContainer(level, pos).map(container -> canAccept(container, stack)).orElse(false);
    }

    /** Removes matching items from one specific registered container. */
    public Extraction extractAt(ServerLevel level, BlockPos pos, Predicate<ItemStack> predicate, int amount) {
        if (level == null || pos == null || predicate == null || amount <= 0
                || !containerPositions.contains(pos.asLong()) || !level.hasChunkAt(pos)) {
            return Extraction.EMPTY;
        }
        Container container = liveContainer(level, pos).orElse(null);
        if (container == null) {
            containerPositions.remove(pos.asLong());
            managers.remove(pos.asLong());
            setDirty();
            return Extraction.EMPTY;
        }

        int remaining = amount;
        ArrayList<ItemStack> extracted = new ArrayList<>();
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            ItemStack removed = container.removeItem(slot, Math.min(remaining, stack.getCount()));
            if (!removed.isEmpty()) {
                extracted.add(removed);
                remaining -= removed.getCount();
                changed = true;
            }
        }
        if (changed) {
            container.setChanged();
        }
        if (extracted.isEmpty()) {
            return Extraction.EMPTY;
        }
        return new Extraction(List.copyOf(extracted), List.of(pos.immutable()));
    }

    /**
     * Removes up to {@code amount} matching items from the nearest loaded registered containers.
     * Returned stacks preserve their real item/NBT/components; no synthetic replacement stack is created.
     */
    public Extraction extractNearest(ServerLevel level, BlockPos origin,
                                     Predicate<ItemStack> predicate, int amount, double maxDistance) {
        if (level == null || predicate == null || amount <= 0) {
            return Extraction.EMPTY;
        }

        int remaining = amount;
        ArrayList<ItemStack> extracted = new ArrayList<>();
        ArrayList<BlockPos> sources = new ArrayList<>();
        for (BlockPos pos : validLoadedPositions(level, origin)) {
            if (origin != null && maxDistance >= 0.0D
                    && distanceSquared(origin, pos) > maxDistance * maxDistance) {
                continue;
            }
            Container container = liveContainer(level, pos).orElse(null);
            if (container == null) {
                continue;
            }

            boolean changed = false;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !predicate.test(stack)) {
                    continue;
                }
                ItemStack removed = container.removeItem(slot, Math.min(remaining, stack.getCount()));
                if (!removed.isEmpty()) {
                    extracted.add(removed);
                    sources.add(pos.immutable());
                    remaining -= removed.getCount();
                    changed = true;
                }
            }
            if (changed) {
                container.setChanged();
            }
            if (remaining <= 0) {
                break;
            }
        }
        return extracted.isEmpty()
                ? Extraction.EMPTY
                : new Extraction(List.copyOf(extracted), List.copyOf(sources));
    }

    /** Inserts into one specific registered container and returns a copy of the remainder. */
    public ItemStack insertAt(ServerLevel level, BlockPos pos, ItemStack offered) {
        if (level == null || pos == null || offered == null || offered.isEmpty()
                || !containerPositions.contains(pos.asLong()) || !level.hasChunkAt(pos)) {
            return offered == null ? ItemStack.EMPTY : offered.copy();
        }
        Container container = liveContainer(level, pos).orElse(null);
        if (container == null) {
            containerPositions.remove(pos.asLong());
            managers.remove(pos.asLong());
            setDirty();
            return offered.copy();
        }
        ItemStack remaining = offered.copy();
        insertInto(container, remaining);
        return remaining;
    }

    /**
     * Inserts as much of {@code offered} as possible into the nearest loaded registered containers.
     * Returns a copy of the remainder; the caller's stack is never mutated.
     */
    public ItemStack insertNearest(ServerLevel level, BlockPos origin, ItemStack offered, double maxDistance) {
        if (level == null || offered == null || offered.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = offered.copy();
        for (BlockPos pos : validLoadedPositions(level, origin)) {
            if (origin != null && maxDistance >= 0.0D
                    && distanceSquared(origin, pos) > maxDistance * maxDistance) {
                continue;
            }
            Container container = liveContainer(level, pos).orElse(null);
            if (container == null) {
                continue;
            }
            if (insertInto(container, remaining) && remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    private List<BlockPos> validLoadedPositions(ServerLevel level, BlockPos origin) {
        ArrayList<BlockPos> valid = new ArrayList<>();
        ArrayList<Long> stale = new ArrayList<>();
        for (long packed : containerPositions) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            if (liveContainer(level, pos).isEmpty()) {
                stale.add(packed);
                continue;
            }
            valid.add(pos.immutable());
        }
        if (!stale.isEmpty()) {
            containerPositions.removeAll(stale);
            stale.forEach(managers::remove);
            setDirty();
        }
        if (origin != null) {
            valid.sort(Comparator.comparingDouble(pos -> distanceSquared(origin, pos)));
        }
        return valid;
    }

    private static Optional<Container> liveContainer(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    static boolean canAcceptAny(Container container, List<ItemStack> offered) {
        return offered.stream().anyMatch(stack -> stack != null && !stack.isEmpty() && canAccept(container, stack));
    }

    static boolean canAccept(Container container, ItemStack offered) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && container.canPlaceItem(slot, offered) && ItemStack.isSameItemSameTags(existing, offered)) {
                int limit = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
                if (existing.getCount() < limit) {
                    return true;
                }
            }
            if (existing.isEmpty() && container.canPlaceItem(slot, offered) && Math.min(container.getMaxStackSize(), offered.getMaxStackSize()) > 0) {
                return true;
            }
        }
        return false;
    }

    static boolean insertInto(Container container, ItemStack remaining) {
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !container.canPlaceItem(slot, remaining) || !ItemStack.isSameItemSameTags(existing, remaining)) {
                continue;
            }
            int limit = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
            int room = limit - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            changed = true;
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int limit = Math.min(container.getMaxStackSize(), remaining.getMaxStackSize());
            int moved = Math.min(limit, remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            container.setItem(slot, placed);
            remaining.shrink(moved);
            changed = true;
        }
        if (changed) {
            container.setChanged();
        }
        return changed;
    }

    private static double distanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putLongArray(TAG_CONTAINERS, containerPositions.stream().mapToLong(Long::longValue).toArray());
        var entries = new ListTag();
        managers.forEach((pos, owner) -> { if (containerPositions.contains(pos)) {
            var tag = new CompoundTag();tag.putLong("Pos",pos);tag.putUUID("Player",owner);entries.add(tag);
        }});
        root.put("Managers",entries);
        return root;
    }

    static StorageData load(CompoundTag root) {
        StorageData data = new StorageData();
        for (long packed : root.getLongArray(TAG_CONTAINERS)) {
            data.containerPositions.add(packed);
            if (data.containerPositions.size() >= MAX_REGISTERED_CONTAINERS) {
                break;
            }
        }
        var entries = root.getList("Managers",Tag.TAG_COMPOUND);
        for (int i=0;i<entries.size() && i<MAX_REGISTERED_CONTAINERS;i++) {
            var tag=entries.getCompound(i);long pos=tag.getLong("Pos");
            if (tag.contains("Pos",Tag.TAG_LONG) && tag.hasUUID("Player") && data.containerPositions.contains(pos))
                data.managers.putIfAbsent(pos,tag.getUUID("Player"));
        }
        return data;
    }

    public enum RegisterResult {
        ADDED,
        ALREADY_REGISTERED,
        NOT_A_CONTAINER,
        LIMIT_REACHED,
        FORBIDDEN
    }

    public record Extraction(List<ItemStack> stacks, List<BlockPos> sourcePositions) {
        private static final Extraction EMPTY = new Extraction(List.of(), List.of());

        public Extraction {
            stacks = stacks == null ? List.of() : List.copyOf(stacks);
            sourcePositions = sourcePositions == null ? List.of() : List.copyOf(sourcePositions);
        }

        public int totalCount() {
            return stacks.stream().mapToInt(ItemStack::getCount).sum();
        }

        public boolean isEmpty() {
            return stacks.isEmpty();
        }
    }
}
