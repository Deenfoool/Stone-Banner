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
    public static final ForgeConfigSpec.DoubleValue CAMERA_ROTATION_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue CAMERA_PAN_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue CAMERA_ZOOM_SENSITIVITY;
    public static final ForgeConfigSpec.BooleanValue CAMERA_INVERT_VERTICAL;
    public static final ForgeConfigSpec.BooleanValue CAMERA_EDGE_PAN;
    public static final ForgeConfigSpec.BooleanValue SHOW_PATH_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue DOUBLE_CLICK_RUN;
    public static final ForgeConfigSpec.IntValue DOUBLE_CLICK_MS;
    public static final ForgeConfigSpec.IntValue HELD_PATH_INTERVAL;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Stone & Banner client settings");

        BUILDER.push("controls");
        CONTROL_MODE = BUILDER
                .comment("ACTION uses WASD; HYBRID uses mouse movement. Legacy TACTICAL is migrated to mouse movement.")
                .defineEnum("controlMode", ControlMode.ACTION);
        SHOW_PATH_PREVIEW = BUILDER
                .comment("Show the planned route when moving with the mouse.")
                .define("showPathPreview", true);
        DOUBLE_CLICK_RUN=BUILDER.comment("Double ground click starts running in Mouse profile.").define("doubleClickRun",true);
        DOUBLE_CLICK_MS=BUILDER.defineInRange("doubleClickMs",300,150,600);
        HELD_PATH_INTERVAL=BUILDER.comment("Ticks between held-cursor path queries (20 ticks per second).").defineInRange("heldPathInterval",5,5,20);
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
        CAMERA_ROTATION_SENSITIVITY = BUILDER
                .comment("Camera rotation multiplier, independent of hero aiming sensitivity.")
                .defineInRange("rotationSensitivity", 1.0D, 0.1D, 4.0D);
        CAMERA_PAN_SENSITIVITY = BUILDER
                .comment("Tactical camera movement multiplier for keys, edge scrolling and Shift + middle drag.")
                .defineInRange("panSensitivity", 1.0D, 0.1D, 4.0D);
        CAMERA_ZOOM_SENSITIVITY = BUILDER
                .comment("Mouse-wheel zoom multiplier.")
                .defineInRange("zoomSensitivity", 1.0D, 0.1D, 4.0D);
        CAMERA_INVERT_VERTICAL = BUILDER
                .comment("Invert vertical camera rotation; does not invert hero aiming.")
                .define("invertVertical", false);
        CAMERA_EDGE_PAN = BUILDER
                .comment("Move the tactical camera when the cursor reaches the screen edge.")
                .define("edgePan", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }

    public static ControlMode controlMode() {
        return CONTROL_MODE.get() == ControlMode.TACTICAL ? ControlMode.HYBRID : CONTROL_MODE.get();
    }

    public static void setControlMode(ControlMode mode) {
        CONTROL_MODE.set(mode);
    }
}
