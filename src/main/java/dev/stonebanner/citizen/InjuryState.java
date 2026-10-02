package dev.stonebanner.citizen;

import java.util.Locale;

public enum InjuryState {
    NORMAL,
    WOUNDED,
    HEAVY_WOUND,
    FRACTURE,
    MISSING;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static InjuryState fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        for (InjuryState state : values()) {
            if (state.serializedName().equalsIgnoreCase(value)) {
                return state;
            }
        }
        return NORMAL;
    }
}
