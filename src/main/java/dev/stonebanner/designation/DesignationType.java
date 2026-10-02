package dev.stonebanner.designation;

import dev.stonebanner.citizen.WorkType;

import javax.annotation.Nullable;
import java.util.Locale;

/** Player-facing work designation operation. */
public enum DesignationType {
    CHOP(WorkType.FORESTRY),
    MINE(WorkType.MINING),
    CANCEL(null);

    private final WorkType workType;

    DesignationType(@Nullable WorkType workType) {
        this.workType = workType;
    }

    @Nullable
    public WorkType workType() {
        return workType;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DesignationType byId(int id) {
        DesignationType[] values = values();
        return id >= 0 && id < values.length ? values[id] : CANCEL;
    }
}
