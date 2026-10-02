package dev.stonebanner.citizen;

public enum WorkPriority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4),
    DISABLED(-1);

    private final int code;

    WorkPriority(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static WorkPriority fromCode(int code) {
        for (WorkPriority priority : values()) {
            if (priority.code == code) {
                return priority;
            }
        }
        return NORMAL;
    }
}
