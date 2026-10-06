package dev.stonebanner.designation;

/** Stable status for why an excavation can or cannot expose its current slice. */
public enum ExcavationAccessStatus {
    READY(0, "message.stonebanner.excavation_access.ready"),
    NO_PATH(1, "message.stonebanner.excavation_access.no_path"),
    NEEDS_LADDER(2, "message.stonebanner.excavation_access.needs_ladder"),
    BLOCKED(3, "message.stonebanner.excavation_access.blocked");

    private final int code;
    private final String translationKey;

    ExcavationAccessStatus(int code, String translationKey) {
        this.code = code;
        this.translationKey = translationKey;
    }

    public int code() {
        return code;
    }

    public String translationKey() {
        return translationKey;
    }

    public static ExcavationAccessStatus fromCode(int code) {
        for (ExcavationAccessStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return NO_PATH;
    }
}
