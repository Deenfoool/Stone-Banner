package dev.stonebanner.designation;

import java.util.Locale;

/** Player-selected access strategy for vertical excavation plans. */
public enum ExcavationAccessMode {
    /** Prefer the physical quarry ramp when geometry supports it, otherwise fall back to ladders. */
    AUTO,
    /** Preserve a physical perimeter staircase/ramp and never substitute ladders. */
    RAMP,
    /** Mine the full footprint and extend a real vanilla ladder line from registered storage. */
    LADDERS;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExcavationAccessMode byId(int id) {
        ExcavationAccessMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : AUTO;
    }

    public static ExcavationAccessMode fromSerializedName(String value) {
        if (value != null) {
            for (ExcavationAccessMode mode : values()) {
                if (mode.serializedName().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return AUTO;
    }

    public ExcavationAccessMode next() {
        return switch (this) {
            case AUTO -> RAMP;
            case RAMP -> LADDERS;
            case LADDERS -> AUTO;
        };
    }
}
