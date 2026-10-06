package dev.stonebanner.designation;

/** Stable server/client status for why an excavation can or cannot expose its current slice. */
public enum ExcavationAccessStatus {
    READY(0),
    NO_PATH(1),
    NEEDS_LADDER(2),
    BLOCKED(3);

    private final int code;

    ExcavationAccessStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
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
