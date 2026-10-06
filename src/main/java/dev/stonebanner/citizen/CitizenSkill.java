package dev.stonebanner.citizen;

import java.util.Locale;

public enum CitizenSkill {
    AGRICULTURE,
    MINING,
    CONSTRUCTION,
    CRAFTING,
    COMBAT,
    GEOLOGY;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
