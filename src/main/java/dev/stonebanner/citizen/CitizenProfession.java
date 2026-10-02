package dev.stonebanner.citizen;

import java.util.Locale;

public enum CitizenProfession {
    UNEMPLOYED,
    FARMER,
    LUMBERJACK,
    MINER,
    BUILDER,
    CRAFTSMAN,
    TRADER,
    GUARD;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CitizenProfession fromSerializedName(String value) {
        if (value != null) {
            for (CitizenProfession profession : values()) {
                if (profession.serializedName().equalsIgnoreCase(value)) {
                    return profession;
                }
            }
        }
        return UNEMPLOYED;
    }
}
