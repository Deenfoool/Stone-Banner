package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Persistent, intentionally small set of physiological/safety pressures for one Citizen. */
public final class CitizenNeeds {
    public static final double MAX = 100.0D;
    public static final double HUNGRY_THRESHOLD = 60.0D;
    public static final double CRITICAL_HUNGER_THRESHOLD = 85.0D;
    public static final double TIRED_THRESHOLD = 65.0D;
    public static final double CRITICAL_FATIGUE_THRESHOLD = 90.0D;
    public static final double DANGER_THRESHOLD = 50.0D;
    public static final double CRITICAL_DANGER_THRESHOLD = 80.0D;

    private static final String TAG_HUNGER = "Hunger";
    private static final String TAG_FATIGUE = "Fatigue";
    private static final String TAG_DANGER = "Danger";

    private double hunger;
    private double fatigue;
    private double danger;

    /** Called once per second by the owning Citizen simulation. */
    public void tickSecond(boolean sleeping) {
        hunger = clamp(hunger + 0.10D);
        fatigue = clamp(fatigue + (sleeping ? -1.50D : 0.08D));
        danger = clamp(danger - 20.0D);
    }

    public double hunger() {
        return hunger;
    }

    public double fatigue() {
        return fatigue;
    }

    public double danger() {
        return danger;
    }

    public void setHunger(double value) {
        hunger = clamp(value);
    }

    public void setFatigue(double value) {
        fatigue = clamp(value);
    }

    public void setDanger(double value) {
        danger = clamp(value);
    }

    public void eat(double relief) {
        hunger = clamp(hunger - Math.max(0.0D, relief));
    }

    public void rest(double relief) {
        fatigue = clamp(fatigue - Math.max(0.0D, relief));
    }

    public boolean isHungry() {
        return hunger >= HUNGRY_THRESHOLD;
    }

    public boolean isCriticallyHungry() {
        return hunger >= CRITICAL_HUNGER_THRESHOLD;
    }

    public boolean isTired() {
        return fatigue >= TIRED_THRESHOLD;
    }

    public boolean isCriticallyTired() {
        return fatigue >= CRITICAL_FATIGUE_THRESHOLD;
    }

    public boolean isUnsafe() {
        return danger >= DANGER_THRESHOLD;
    }

    public boolean isInCriticalDanger() {
        return danger >= CRITICAL_DANGER_THRESHOLD;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_HUNGER, hunger);
        tag.putDouble(TAG_FATIGUE, fatigue);
        tag.putDouble(TAG_DANGER, danger);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains(TAG_HUNGER, Tag.TAG_DOUBLE)) {
            setHunger(tag.getDouble(TAG_HUNGER));
        }
        if (tag.contains(TAG_FATIGUE, Tag.TAG_DOUBLE)) {
            setFatigue(tag.getDouble(TAG_FATIGUE));
        }
        if (tag.contains(TAG_DANGER, Tag.TAG_DOUBLE)) {
            setDanger(tag.getDouble(TAG_DANGER));
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(MAX, value));
    }
}
