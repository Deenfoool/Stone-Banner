package dev.stonebanner.client.control;

/** Client-only compact storage summary populated by the server. */
public final class StorageSummaryState {
    private static int ladderCount;

    private StorageSummaryState() {
    }

    public static void accept(int ladders) {
        ladderCount = Math.max(0, ladders);
    }

    public static int ladderCount() {
        return ladderCount;
    }

    public static void clear() {
        ladderCount = 0;
    }
}
