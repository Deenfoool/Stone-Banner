package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small persistent personal inventory shared by every Citizen implementation.
 *
 * <p>It stores real Minecraft ItemStacks. Logistics uses the same slots and marks only the stacks currently
 * carried as work cargo, so mined resources never become a hidden counter or a second virtual inventory.</p>
 */
public final class CitizenInventory {
    public static final int SLOT_COUNT = 9;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_HAUL_CARGO = "HaulCargo";

    private final List<ItemStack> slots = new ArrayList<>(SLOT_COUNT);
    private final boolean[] haulCargo = new boolean[SLOT_COUNT];

    public CitizenInventory() {
        for (int index = 0; index < SLOT_COUNT; index++) {
            slots.add(ItemStack.EMPTY);
        }
    }

    public ItemStack stack(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return slots.get(slot);
    }

    public List<ItemStack> snapshot() {
        return slots.stream().map(ItemStack::copy).toList();
    }

    /**
     * Inserts personal inventory content and never merges it into a slot reserved for work cargo.
     * The caller retains ownership of its original stack; this method only works on a copy.
     */
    public ItemStack add(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = incoming.copy();
        for (int index = 0; index < SLOT_COUNT && !remainder.isEmpty(); index++) {
            ItemStack existing = slots.get(index);
            if (haulCargo[index] || existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
                continue;
            }
            int room = Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (room == 0) {
                continue;
            }
            int moved = Math.min(room, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
        }

        for (int index = 0; index < SLOT_COUNT && !remainder.isEmpty(); index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(remainder.getMaxStackSize(), remainder.getCount());
            ItemStack inserted = remainder.copy();
            inserted.setCount(moved);
            slots.set(index, inserted);
            haulCargo[index] = false;
            remainder.shrink(moved);
        }
        return remainder;
    }

    /**
     * Inserts real work drops into the same inventory while marking exactly which slots must later be hauled.
     * Cargo only merges with other cargo, so personal food/tools are never silently deposited into storage.
     */
    public ItemStack addHaulCargo(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = incoming.copy();
        for (int index = 0; index < SLOT_COUNT && !remainder.isEmpty(); index++) {
            ItemStack existing = slots.get(index);
            if (!haulCargo[index] || existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
                continue;
            }
            int room = Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (room == 0) {
                continue;
            }
            int moved = Math.min(room, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
        }

        for (int index = 0; index < SLOT_COUNT && !remainder.isEmpty(); index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(remainder.getMaxStackSize(), remainder.getCount());
            ItemStack inserted = remainder.copy();
            inserted.setCount(moved);
            slots.set(index, inserted);
            haulCargo[index] = true;
            remainder.shrink(moved);
        }
        return remainder;
    }

    public int countPersonalItem(Item item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = slots.get(index);
            if (!haulCargo[index] && !stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} from personal (non-haul) slots and returns the real removed stack. */
    public ItemStack removePersonalItem(Item item, int amount) {
        if (item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = slots.get(index);
            if (haulCargo[index] || stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            ItemStack removed = stack.split(Math.min(amount, stack.getCount()));
            if (stack.isEmpty()) {
                slots.set(index, ItemStack.EMPTY);
            }
            return removed;
        }
        return ItemStack.EMPTY;
    }

    public boolean hasHaulCargo() {
        for (int index = 0; index < SLOT_COUNT; index++) {
            if (haulCargo[index] && !slots.get(index).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Optional<HaulCargo> firstHaulCargo() {
        for (int index = 0; index < SLOT_COUNT; index++) {
            if (haulCargo[index] && !slots.get(index).isEmpty()) {
                return Optional.of(new HaulCargo(index, slots.get(index).copy()));
            }
        }
        return Optional.empty();
    }

    public List<HaulCargo> haulCargoSnapshot() {
        ArrayList<HaulCargo> cargo = new ArrayList<>();
        for (int index = 0; index < SLOT_COUNT; index++) {
            if (haulCargo[index] && !slots.get(index).isEmpty()) {
                cargo.add(new HaulCargo(index, slots.get(index).copy()));
            }
        }
        return List.copyOf(cargo);
    }

    /** Replaces one marked cargo slot after a partial/full storage insertion. */
    public void setHaulCargoStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            slots.set(slot, ItemStack.EMPTY);
            haulCargo[slot] = false;
            return;
        }
        slots.set(slot, stack.copy());
        haulCargo[slot] = true;
    }

    public boolean hasFood(LivingEntity eater) {
        return firstFoodSlot(eater).isPresent();
    }

    /**
     * Consumes one real food use from the first personal edible stack.
     * ItemStack.finishUsingItem is intentionally used so modded foods and vanilla food side-effects execute.
     */
    public Optional<FoodConsumption> consumeFood(LivingEntity eater) {
        if (eater == null) {
            return Optional.empty();
        }

        Optional<Integer> slot = firstFoodSlot(eater);
        if (slot.isEmpty()) {
            return Optional.empty();
        }

        int index = slot.get();
        ItemStack current = slots.get(index);
        FoodProperties properties = current.getFoodProperties(eater);
        if (properties == null) {
            return Optional.empty();
        }

        String itemDescriptionId = current.getDescriptionId();
        int nutrition = Math.max(0, properties.getNutrition());
        ItemStack result = current.finishUsingItem(eater.level(), eater);
        slots.set(index, result == null || result.isEmpty() ? ItemStack.EMPTY : result);
        return Optional.of(new FoodConsumption(index, itemDescriptionId, nutrition));
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag items = new ListTag();
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = slots.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte(TAG_SLOT, (byte) index);
            entry.putBoolean(TAG_HAUL_CARGO, haulCargo[index]);
            stack.save(entry);
            items.add(entry);
        }
        root.put(TAG_ITEMS, items);
        return root;
    }

    public void load(CompoundTag root) {
        clear();
        if (root == null || !root.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            return;
        }

        ListTag items = root.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            int slot = entry.getByte(TAG_SLOT) & 0xFF;
            if (slot < 0 || slot >= SLOT_COUNT) {
                continue;
            }
            ItemStack stack = ItemStack.of(entry);
            slots.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            haulCargo[slot] = !stack.isEmpty() && entry.getBoolean(TAG_HAUL_CARGO);
        }
    }

    public void clear() {
        for (int index = 0; index < SLOT_COUNT; index++) {
            slots.set(index, ItemStack.EMPTY);
            haulCargo[index] = false;
        }
    }

    private Optional<Integer> firstFoodSlot(LivingEntity eater) {
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = slots.get(index);
            if (!haulCargo[index] && !stack.isEmpty() && stack.getFoodProperties(eater) != null) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    public record HaulCargo(int slot, ItemStack stack) {
        public HaulCargo {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }

    public record FoodConsumption(int slot, String itemDescriptionId, int nutrition) {
        public double hungerRelief() {
            return nutrition * (CitizenNeeds.MAX / 20.0D);
        }
    }
}
