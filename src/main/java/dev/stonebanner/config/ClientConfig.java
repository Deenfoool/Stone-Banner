package dev.stonebanner.config;

import dev.stonebanner.control.ControlMode;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<ControlMode> CONTROL_MODE;
    public static final ForgeConfigSpec.BooleanValue ENFORCE_THIRD_PERSON;
    public static final ForgeConfigSpec.DoubleValue CAMERA_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue CAMERA_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue CAMERA_PITCH;
    public static final ForgeConfigSpec.DoubleValue CAMERA_SMOOTHING;
    public static final ForgeConfigSpec.BooleanValue SHOW_PATH_PREVIEW;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Stone & Banner client settings");

        BUILDER.push("controls");
        CONTROL_MODE = BUILDER
                .comment("ACTION uses WASD, TACTICAL uses mouse movement, HYBRID supports both.")
                .defineEnum("controlMode", ControlMode.ACTION);
        SHOW_PATH_PREVIEW = BUILDER
                .comment("Show the planned route when moving with the mouse.")
                .define("showPathPreview", true);
        BUILDER.pop();

        BUILDER.push("camera");
        ENFORCE_THIRD_PERSON = BUILDER
                .comment("Keep the player in the Stone & Banner third-person perspective.")
                .define("enforceThirdPerson", true);
        CAMERA_DISTANCE = BUILDER
                .comment("Preferred camera distance. The custom camera system will use this value.")
                .defineInRange("distance", 8.0D, 2.0D, 24.0D);
        CAMERA_HEIGHT = BUILDER
                .comment("Preferred camera height above the player.")
                .defineInRange("height", 3.0D, 0.0D, 12.0D);
        CAMERA_PITCH = BUILDER
                .comment("Camera pitch used when entering a world. Positive values look downward.")
                .defineInRange("pitch", 35.0D, -15.0D, 80.0D);
        CAMERA_SMOOTHING = BUILDER
                .comment("Camera smoothing, from immediate (0) to very smooth (1).")
                .defineInRange("smoothing", 0.35D, 0.0D, 1.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }

    public static ControlMode controlMode() {
        return CONTROL_MODE.get();
    }

    public static void setControlMode(ControlMode mode) {
        CONTROL_MODE.set(mode);
        save();
    }

    public static void save() {
        SPEC.save();
    }
}
