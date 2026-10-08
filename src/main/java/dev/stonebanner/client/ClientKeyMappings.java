package dev.stonebanner.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stonebanner.StoneAndBanner;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.stonebanner";
    public static final KeyMapping BUILDING=new KeyMapping("key.stonebanner.building",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,CATEGORY);
    public static final KeyMapping PRODUCTION=new KeyMapping("key.stonebanner.production",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,CATEGORY);

    public static final KeyMapping CYCLE_CONTROL_MODE = new KeyMapping(
            "key.stonebanner.cycle_control_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping CYCLE_DESIGNATION_MODE = new KeyMapping(
            "key.stonebanner.cycle_designation_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static final KeyMapping ORE_JOURNAL = new KeyMapping(
            "key.stonebanner.ore_journal", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    public static final KeyMapping DEBUG_OVERLAY = new KeyMapping(
            "key.stonebanner.debug_overlay", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);

    public static final KeyMapping FOCUS_SELECTED = new KeyMapping(
            "key.stonebanner.focus_selected", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY);
    public static final KeyMapping RECENTER_CAMERA = new KeyMapping(
            "key.stonebanner.recenter_camera", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY);

    public static final KeyMapping LAYER_BOUNDARIES = new KeyMapping("key.stonebanner.layer_boundaries", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY);
    public static final KeyMapping LAYER_RESOURCES = new KeyMapping("key.stonebanner.layer_resources", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);
    public static final KeyMapping LAYER_FERTILITY = new KeyMapping("key.stonebanner.layer_fertility", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    public static final KeyMapping CAMERA_ROTATE = new KeyMapping("key.stonebanner.camera_rotate",KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_MIDDLE,CATEGORY);
    public static final KeyMapping ALTERNATIVE_USE = binding("alternative_use", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ORDERS = binding("orders", GLFW.GLFW_KEY_TAB);
    public static final KeyMapping STOP = binding("stop", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping ROTATE_BLUEPRINT = binding("rotate_blueprint", GLFW.GLFW_KEY_R);
    public static final KeyMapping CAMERA_UP = binding("camera_up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping CAMERA_DOWN = binding("camera_down", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping CONTROLS_HELP = binding("controls_help", GLFW.GLFW_KEY_F4);
    public static final KeyMapping[] SAVE_GROUP = new KeyMapping[9];
    public static final KeyMapping[] RECALL_GROUP = new KeyMapping[9];
    static {
        for (int i = 0; i < 9; i++) {
            SAVE_GROUP[i] = new KeyMapping("key.stonebanner.save_group_" + (i+1), KeyConflictContext.IN_GAME,
                    net.minecraftforge.client.settings.KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1+i, CATEGORY);
            RECALL_GROUP[i] = new KeyMapping("key.stonebanner.recall_group_" + (i+1), KeyConflictContext.IN_GAME,
                    net.minecraftforge.client.settings.KeyModifier.ALT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1+i, CATEGORY);
        }
    }
    private static KeyMapping binding(String name, int key) {
        return new KeyMapping("key.stonebanner."+name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }
    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CAMERA_ROTATE); event.register(ALTERNATIVE_USE); event.register(ORDERS); event.register(STOP); event.register(ROTATE_BLUEPRINT);
        event.register(CAMERA_UP); event.register(CAMERA_DOWN); event.register(CONTROLS_HELP);
        for (int i=0;i<9;i++) { event.register(SAVE_GROUP[i]); event.register(RECALL_GROUP[i]); }
        event.register(PRODUCTION);event.register(BUILDING);
        event.register(ORE_JOURNAL);
        event.register(DEBUG_OVERLAY);
        event.register(FOCUS_SELECTED);
        event.register(RECENTER_CAMERA);
        event.register(LAYER_BOUNDARIES); event.register(LAYER_RESOURCES); event.register(LAYER_FERTILITY);
        event.register(CYCLE_CONTROL_MODE);
        event.register(CYCLE_DESIGNATION_MODE);
    }
}
