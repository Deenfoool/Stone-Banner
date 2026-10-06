package dev.stonebanner.citizen;

import java.util.Locale;

/** Runtime diagnostic for the worker; the job and inventory remain authoritative. */
public enum WorkBlockReason {
    NONE, NO_PATH, OCCUPIED, ORE_PERMISSION, UNSAFE_EXIT;
    public String key() { return "work_block.stonebanner." + name().toLowerCase(Locale.ROOT); }
    public static WorkBlockReason byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }
}
