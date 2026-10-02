package dev.stonebanner.citizen;

import java.util.Locale;

public enum BodyPart {
    HEAD,
    TORSO,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
