package dev.stonebanner.citizen;

import java.util.Locale;

public enum WorkType {
    EMERGENCY,
    TREATMENT,
    FARMING,
    FORESTRY,
    MINING,
    CLEARING,
    BUILDING,
    CRAFTING,
    HAULING,
    TRADING,
    GUARD;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
