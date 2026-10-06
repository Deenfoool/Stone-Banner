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

    public static final KeyMapping LAYER_BOUNDARIES = new KeyMapping("key.stonebanner.layer_boundaries", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY);
    public static final KeyMapping LAYER_RESOURCES = new KeyMapping("key.stonebanner.layer_resources", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);
    public static final KeyMapping LAYER_FERTILITY = new KeyMapping("key.stonebanner.layer_fertility", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ORE_JOURNAL);
        event.register(LAYER_BOUNDARIES); event.register(LAYER_RESOURCES); event.register(LAYER_FERTILITY);
        event.register(CYCLE_CONTROL_MODE);
        event.register(CYCLE_DESIGNATION_MODE);
    }
}
