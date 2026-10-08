package dev.stonebanner.control;

/** Pure preset selection; old custom TOML values survive until a player clicks the setting. */
public final class SettingPresets {
    private SettingPresets() {}

    public static int nextIndex(int[] values, int current) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("No presets");
        for (int i = 0; i < values.length; i++) if (values[i] > current) return i;
        return 0;
    }

    public static int nextIndex(double[] values, double current) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("No presets");
        for (int i = 0; i < values.length; i++) if (values[i] > current + 0.00001D) return i;
        return 0;
    }
}
