package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;

/** Persistent localized body damage for one Citizen. */
public final class CitizenHealth {
    private static final String TAG_PARTS = "Parts";

    private final EnumMap<BodyPart, InjuryState> injuries = new EnumMap<>(BodyPart.class);

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
        }
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
        return root;
    }

    public void load(CompoundTag root) {
        if (!root.contains(TAG_PARTS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag parts = root.getCompound(TAG_PARTS);
        for (BodyPart part : BodyPart.values()) {
            if (parts.contains(part.serializedName(), Tag.TAG_STRING)) {
                setInjury(part, InjuryState.fromSerializedName(parts.getString(part.serializedName())));
            }
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
