package dev.stonebanner.client.hud;

import java.util.Optional;

/** Keeps diagnostics below the resource bar and above the bottom control dock. */
public record DebugPanelLayout(int x, int y, int width, int rows) {
    public int height() {
        return rows * 10 + 8;
    }

    public static Optional<DebugPanelLayout> fit(int screenWidth, int screenHeight, int requestedRows) {
        int width = Math.min(320, screenWidth - 16);
        int rows = Math.min(requestedRows, (screenHeight - 56 - 100 - 8) / 10);
        if (width < 100 || rows < 2) return Optional.empty();
        return Optional.of(new DebugPanelLayout(screenWidth - width - 8, 56, width, rows));
    }
}
