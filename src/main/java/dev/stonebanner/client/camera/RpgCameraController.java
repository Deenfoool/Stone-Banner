package dev.stonebanner.client.camera;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, value = Dist.CLIENT)
public final class RpgCameraController {
    private static final double MIN_DISTANCE = 2.0D;
    private static final double MAX_DISTANCE = 24.0D;
    private static final double ZOOM_STEP = 1.0D;
    private static final float MIN_PITCH = -15.0F;
    private static final float MAX_PITCH = 80.0F;
    private static final int SAVE_DELAY_TICKS = 20;

    private static double targetDistance = Double.NaN;
    private static double currentDistance = Double.NaN;
    private static Player trackedPlayer;
    private static float cameraYaw;
    private static float cameraPitch;
    private static float lastPlayerYaw;
    private static float lastPlayerPitch;
    private static boolean rotatingCamera;
    private static int saveCountdown;

    private RpgCameraController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ensureInitialized();
        updateSmoothedDistance();
        saveChangedDistanceWhenReady();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!isCameraActive(minecraft)) {
            trackedPlayer = null;
            rotatingCamera = false;
            return;
        }

        initializeOrientationIfNeeded(minecraft.player);
        captureCameraRotation(minecraft);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && isCameraActive(Minecraft.getInstance())) {
            rotatingCamera = event.getAction() != GLFW.GLFW_RELEASE;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isCameraActive(minecraft) || minecraft.screen != null || event.getScrollDelta() == 0.0D) {
            return;
        }

        ensureInitialized();
        targetDistance = Mth.clamp(
                targetDistance - event.getScrollDelta() * ZOOM_STEP,
                MIN_DISTANCE,
                MAX_DISTANCE
        );
        ClientConfig.CAMERA_DISTANCE.set(targetDistance);
        saveCountdown = SAVE_DELAY_TICKS;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isCameraActive(minecraft)) {
            return;
        }

        ensureInitialized();
        initializeOrientationIfNeeded(minecraft.player);

        Camera camera = event.getCamera();
        Entity focusedEntity = camera.getEntity();
        if (focusedEntity == null) {
            return;
        }

        event.setYaw(cameraYaw);
        event.setPitch(cameraPitch);
        camera.setRotation(cameraYaw, cameraPitch);

        float partialTick = (float) event.getPartialTick();
        Vec3 anchor = focusedEntity.getEyePosition(partialTick)
                .add(0.0D, ClientConfig.CAMERA_HEIGHT.get(), 0.0D);

        camera.setPosition(anchor);
        double collisionSafeDistance = camera.getMaxZoom(currentDistance);
        Vector3f look = camera.getLookVector();
        camera.setPosition(anchor.subtract(
                look.x() * collisionSafeDistance,
                look.y() * collisionSafeDistance,
                look.z() * collisionSafeDistance
        ));
    }

    private static boolean isCameraActive(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.level != null
                && ClientConfig.ENFORCE_THIRD_PERSON.get()
                && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }

    private static void ensureInitialized() {
        if (Double.isNaN(targetDistance)) {
            targetDistance = ClientConfig.CAMERA_DISTANCE.get();
        }
        if (Double.isNaN(currentDistance)) {
            currentDistance = targetDistance;
        }
    }

    private static void initializeOrientationIfNeeded(Player player) {
        if (trackedPlayer == player) {
            return;
        }

        trackedPlayer = player;
        cameraYaw = player.getYRot();
        cameraPitch = Mth.clamp(ClientConfig.CAMERA_PITCH.get().floatValue(), MIN_PITCH, MAX_PITCH);
        lastPlayerYaw = player.getYRot();
        lastPlayerPitch = player.getXRot();
    }

    private static void captureCameraRotation(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        boolean middleButtonDown = GLFW.glfwGetMouseButton(
                minecraft.getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE
        ) == GLFW.GLFW_PRESS;
        if (!middleButtonDown) {
            rotatingCamera = false;
        }

        float currentPlayerYaw = player.getYRot();
        float currentPlayerPitch = player.getXRot();

        if (rotatingCamera && minecraft.screen == null) {
            float yawDelta = Mth.wrapDegrees(currentPlayerYaw - lastPlayerYaw);
            float pitchDelta = currentPlayerPitch - lastPlayerPitch;

            cameraYaw = Mth.wrapDegrees(cameraYaw + yawDelta);
            cameraPitch = Mth.clamp(cameraPitch + pitchDelta, MIN_PITCH, MAX_PITCH);

            player.setYRot(lastPlayerYaw);
            player.setXRot(lastPlayerPitch);
            player.yRotO = lastPlayerYaw;
            player.xRotO = lastPlayerPitch;

            ClientConfig.CAMERA_PITCH.set((double) cameraPitch);
            saveCountdown = SAVE_DELAY_TICKS;
        }

        lastPlayerYaw = player.getYRot();
        lastPlayerPitch = player.getXRot();
    }

    public static float cameraYaw() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            initializeOrientationIfNeeded(player);
        }
        return cameraYaw;
    }

    public static void rotateByMouseDrag(double dragX, double dragY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isCameraActive(minecraft) || minecraft.player == null) {
            return;
        }

        initializeOrientationIfNeeded(minecraft.player);
        cameraYaw = Mth.wrapDegrees(cameraYaw - (float) dragX * 0.45F);
        cameraPitch = Mth.clamp(cameraPitch + (float) dragY * 0.45F, MIN_PITCH, MAX_PITCH);
        ClientConfig.CAMERA_PITCH.set((double) cameraPitch);
        saveCountdown = SAVE_DELAY_TICKS;
    }

    public static void adjustZoom(double scrollDelta) {
        if (scrollDelta == 0.0D) {
            return;
        }
        ensureInitialized();
        targetDistance = Mth.clamp(targetDistance - scrollDelta * ZOOM_STEP, MIN_DISTANCE, MAX_DISTANCE);
        ClientConfig.CAMERA_DISTANCE.set(targetDistance);
        saveCountdown = SAVE_DELAY_TICKS;
    }

    private static void updateSmoothedDistance() {
        double smoothing = ClientConfig.CAMERA_SMOOTHING.get();
        double response = Math.max(0.05D, 1.0D - smoothing);
        currentDistance = Mth.lerp(response, currentDistance, targetDistance);

        if (Math.abs(currentDistance - targetDistance) < 0.001D) {
            currentDistance = targetDistance;
        }
    }

    private static void saveChangedDistanceWhenReady() {
        if (saveCountdown <= 0) {
            return;
        }

        saveCountdown--;
        if (saveCountdown == 0) {
            ClientConfig.save();
        }
    }
}
