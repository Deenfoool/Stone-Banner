package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small persistent personal inventory shared by every Citizen implementation.
 *
 * It stores real Minecraft ItemStacks. Settlement logistics can later fill/empty the same inventory;
 * no hidden food/material counters are introduced here.
 */
public final class CitizenInventory {
    public static final int SLOT_COUNT = 9;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private final List<ItemStack> slots = new ArrayList<>(SLOT_COUNT);

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
     * Inserts as much of {@code incoming} as possible and returns the remainder.
     * The caller retains ownership of its original stack; this method only works on a copy.
     */
    public ItemStack add(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = incoming.copy();
        for (int index = 0; index < SLOT_COUNT && !remainder.isEmpty(); index++) {
            ItemStack existing = slots.get(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
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
            remainder.shrink(moved);
        }
        return remainder;
    }

    public boolean hasFood(LivingEntity eater) {
        return firstFoodSlot(eater).isPresent();
    }

    /**
     * Consumes one real food use from the first edible stack.
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
        }
    }

    public void clear() {
        for (int index = 0; index < SLOT_COUNT; index++) {
            slots.set(index, ItemStack.EMPTY);
        }
    }

    private Optional<Integer> firstFoodSlot(LivingEntity eater) {
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = slots.get(index);
            if (!stack.isEmpty() && stack.getFoodProperties(eater) != null) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    public record FoodConsumption(int slot, String itemDescriptionId, int nutrition) {
        public double hungerRelief() {
            return nutrition * (CitizenNeeds.MAX / 20.0D);
        }
    }
}
