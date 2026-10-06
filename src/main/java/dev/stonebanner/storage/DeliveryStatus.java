package dev.stonebanner.storage;

import java.util.Locale;

/** Runtime delivery state; cargo itself remains in the persistent Citizen inventory. */
public enum DeliveryStatus {
    IDLE, TRAVELLING, DEPOSITING, WAITING_STORAGE, BLOCKED_ROUTE, PAUSED_NEEDS, PLAYER_COMMAND;

    public String key() { return "delivery.stonebanner." + name().toLowerCase(Locale.ROOT); }
    public static DeliveryStatus byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IDLE;
    }
}
