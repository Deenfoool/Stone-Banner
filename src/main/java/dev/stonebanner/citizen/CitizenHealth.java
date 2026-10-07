package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;

/** Persistent localized body damage for one Citizen. */
public final class CitizenHealth {
    private static final String TAG_PARTS = "Parts";

    private final EnumMap<BodyPart, InjuryState> injuries = new EnumMap<>(BodyPart.class);
    private final EnumMap<BodyPart, Double> trauma = new EnumMap<>(BodyPart.class);
    private final EnumMap<BodyPart, Integer> recovery = new EnumMap<>(BodyPart.class);

    public CitizenHealth() {
        for (BodyPart part : BodyPart.values()) {
            injuries.put(part, InjuryState.NORMAL);
        }
    }

    public InjuryState injury(BodyPart part) {
        return injuries.getOrDefault(part, InjuryState.NORMAL);
    }

    public void setInjury(BodyPart part, InjuryState state) {
        if (part != null && state != null) {
            injuries.put(part, state);
            trauma.put(part, switch (state) { case NORMAL -> 0D; case WOUNDED -> 1D; case HEAVY_WOUND -> 5D; case FRACTURE, MISSING -> 10D; });
            recovery.remove(part);
        }
    }

    /** Only actual lost HP enters this model; armour, absorption and cancelled hits stay vanilla. */
    public void damage(BodyPart part, double amount) {
        if (part == null || !Double.isFinite(amount) || amount <= 0 || injury(part) == InjuryState.MISSING) return;
        double total = Math.min(20, trauma.getOrDefault(part, 0D) + amount);
        boolean limb = part != BodyPart.HEAD && part != BodyPart.TORSO;
        InjuryState state = total >= 10 && limb ? InjuryState.FRACTURE : total >= 5 ? InjuryState.HEAVY_WOUND : InjuryState.WOUNDED;
        // New damage never heals an existing fracture.
        if (state.ordinal() < injury(part).ordinal()) state = injury(part);
        injuries.put(part, state);
        trauma.put(part, total);
        recovery.remove(part);
    }

    public int recoverySeconds(BodyPart part) { return recovery.getOrDefault(part, 0); }

    public boolean canTreat(BodyPart part, boolean splint) {
        if (part == null || recoverySeconds(part) > 0) return false;
        return splint ? injury(part) == InjuryState.FRACTURE
                : injury(part) == InjuryState.WOUNDED || injury(part) == InjuryState.HEAVY_WOUND;
    }

    public boolean treat(BodyPart part, boolean splint) {
        if (!canTreat(part, splint)) return false;
        recovery.put(part, stageSeconds(injury(part)));
        return true;
    }

    public boolean isBleeding() {
        return untreatedCore(BodyPart.HEAD) || untreatedCore(BodyPart.TORSO);
    }

    private boolean untreatedCore(BodyPart part) {
        return isDangerous(injury(part)) && recoverySeconds(part) == 0;
    }

    public boolean needsRecovery() { return !recovery.isEmpty() || hasDangerousCoreInjury(); }

    /** Each stage restores a body state, not just HP. Rest and food are required. */
    public boolean recoverSecond(boolean resting, boolean fed) {
        if (!resting || !fed) return false;
        boolean improved = false;
        for (BodyPart part : BodyPart.values()) {
            int seconds = recoverySeconds(part);
            if (seconds <= 0) continue;
            if (seconds > 1) { recovery.put(part, seconds - 1); continue; }
            InjuryState next = switch (injury(part)) {
                case FRACTURE -> InjuryState.HEAVY_WOUND;
                case HEAVY_WOUND -> InjuryState.WOUNDED;
                case WOUNDED -> InjuryState.NORMAL;
                default -> injury(part);
            };
            setInjury(part, next);
            if (next != InjuryState.NORMAL && next != InjuryState.MISSING) recovery.put(part, stageSeconds(next));
            improved = true;
        }
        return improved;
    }

    private static int stageSeconds(InjuryState state) {
        return switch (state) { case WOUNDED -> 60; case HEAVY_WOUND -> 120; case FRACTURE -> 180; default -> 0; };
    }

    public Map<BodyPart, InjuryState> injuriesView() {
        return Map.copyOf(injuries);
    }

    public double movementMultiplier() {
        double multiplier = 1.0D;
        multiplier *= legMultiplier(injury(BodyPart.LEFT_LEG));
        multiplier *= legMultiplier(injury(BodyPart.RIGHT_LEG));
        return Math.max(0.20D, multiplier);
    }

    /**
     * A Citizen can limp on one badly damaged leg, but two structurally unusable legs prevent
     * independent pathing. Treatment/carrying can later provide alternate mobility without changing
     * the underlying injury model.
     */
    public boolean canMoveIndependently() {
        return !(isImmobilizingLeg(injury(BodyPart.LEFT_LEG))
                && isImmobilizingLeg(injury(BodyPart.RIGHT_LEG)));
    }

    public double workEfficiencyMultiplier() {
        double multiplier = 1.0D;
        multiplier *= armMultiplier(injury(BodyPart.LEFT_ARM));
        multiplier *= armMultiplier(injury(BodyPart.RIGHT_ARM));
        multiplier *= coreWorkMultiplier(injury(BodyPart.HEAD));
        return Math.max(0.20D, multiplier);
    }

    public double combatEfficiencyMultiplier() {
        double multiplier = workEfficiencyMultiplier();
        multiplier *= coreWorkMultiplier(injury(BodyPart.TORSO));
        return Math.max(0.15D, multiplier);
    }

    public boolean hasDangerousCoreInjury() {
        return isDangerous(injury(BodyPart.HEAD)) || isDangerous(injury(BodyPart.TORSO));
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        CompoundTag parts = new CompoundTag();
        for (BodyPart part : BodyPart.values()) {
            parts.putString(part.serializedName(), injury(part).serializedName());
        }
        root.put(TAG_PARTS, parts);
        CompoundTag damage = new CompoundTag(), care = new CompoundTag();
        for (BodyPart part : BodyPart.values()) {
            damage.putDouble(part.serializedName(), trauma.getOrDefault(part, 0D));
            care.putInt(part.serializedName(), recoverySeconds(part));
        }
        root.put("Trauma", damage);
        root.put("Recovery", care);
        return root;
    }

    public void load(CompoundTag root) {
        trauma.clear(); recovery.clear();
        for (BodyPart part : BodyPart.values()) injuries.put(part, InjuryState.NORMAL);
        if (!root.contains(TAG_PARTS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag parts = root.getCompound(TAG_PARTS);
        for (BodyPart part : BodyPart.values()) {
            if (parts.contains(part.serializedName(), Tag.TAG_STRING)) {
                setInjury(part, InjuryState.fromSerializedName(parts.getString(part.serializedName())));
            }
            CompoundTag damage = root.getCompound("Trauma");
            double saved = damage.getDouble(part.serializedName());
            if (damage.contains(part.serializedName(), Tag.TAG_ANY_NUMERIC) && Double.isFinite(saved)) {
                double minimum = switch (injury(part)) { case NORMAL, WOUNDED -> 0D; case HEAVY_WOUND -> 5D; case FRACTURE, MISSING -> 10D; };
                trauma.put(part, injury(part) == InjuryState.NORMAL ? 0D : Math.max(minimum, Math.min(20, saved)));
            }
            int seconds = root.getCompound("Recovery").getInt(part.serializedName());
            int maximum = stageSeconds(injury(part));
            if (seconds > 0 && maximum > 0) recovery.put(part, Math.min(maximum, seconds));
        }
    }

    private static double legMultiplier(InjuryState state) {
        return switch (state) {
            case NORMAL -> 1.0D;
            case WOUNDED -> 0.90D;
            case HEAVY_WOUND -> 0.75D;
            case FRACTURE -> 0.55D;
            case MISSING -> 0.35D;
        };
    }

    private static double armMultiplier(InjuryState state) {
        return switch (state) {
            case NORMAL -> 1.0D;
            case WOUNDED -> 0.92D;
            case HEAVY_WOUND -> 0.78D;
            case FRACTURE -> 0.58D;
            case MISSING -> 0.35D;
        };
    }

    private static double coreWorkMultiplier(InjuryState state) {
        return switch (state) {
            case NORMAL -> 1.0D;
            case WOUNDED -> 0.95D;
            case HEAVY_WOUND -> 0.80D;
            case FRACTURE -> 0.70D;
            case MISSING -> 0.50D;
        };
    }

    private static boolean isDangerous(InjuryState state) {
        return state == InjuryState.HEAVY_WOUND
                || state == InjuryState.FRACTURE
                || state == InjuryState.MISSING;
    }

    private static boolean isImmobilizingLeg(InjuryState state) {
        return state == InjuryState.FRACTURE || state == InjuryState.MISSING;
    }
}
