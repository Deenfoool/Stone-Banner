package dev.stonebanner.citizen;

import java.util.Locale;

/** How far a Citizen is allowed to operate away from its home community. */
public enum CitizenParticipation {
    LOCAL_HELPER,
    COMPANION,
    SETTLER;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CitizenParticipation fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL_HELPER;
        }
        for (CitizenParticipation participation : values()) {
            if (participation.serializedName().equalsIgnoreCase(value)) {
                return participation;
            }
        }
        return LOCAL_HELPER;
    }
}
