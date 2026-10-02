package dev.stonebanner.citizen;

import java.util.Locale;

/** High-level state exposed by Citizen AI and debug/UI code. */
public enum CitizenBrainState {
    // Keep the first three ordinals stable for saves created by the early prototype.
    IDLE,
    MOVE,
    FOLLOW,
    WORK,
    EAT,
    SLEEP,
    FLEE,
    DEFEND,
    TRAVEL,
    MIGRATE,
    RETURN_HOME;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CitizenBrainState byId(int id) {
        CitizenBrainState[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }

    public static CitizenBrainState fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }
        for (CitizenBrainState state : values()) {
            if (state.serializedName().equalsIgnoreCase(value)) {
                return state;
            }
        }
        return IDLE;
    }
}
