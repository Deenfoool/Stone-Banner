package dev.stonebanner.client.camera;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.chunk.ChunkStatus;
import java.util.ArrayList;
import java.util.List;
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
    private static Vec3 focusTarget;
    private static Vec3 focusAnchor;
    private static Vec3 previousFocusAnchor;
    private static CameraFollowTarget followTarget;
    private static net.minecraft.client.multiplayer.ClientLevel followWorld;
    private static boolean viewObstructed;
    private static final TacticalCameraRig tacticalRig = new TacticalCameraRig();
    private static boolean wasTactical;
    private static final CameraAnchorTransition anchorTransition = new CameraAnchorTransition();
    private static CameraView heroCamera;

    private record CameraView(float yaw, float pitch, double distance) {}
    private static Vec3 lastSafeAnchor;
    private static net.minecraft.resources.ResourceLocation cameraDimension;
    public static boolean viewObstructed() { return viewObstructed; }

    public static void focusOn(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.hasChunkAt(pos)) return;
        initializeOrientationIfNeeded(mc.player);
        clearFocus();
        focusAnchor = lastSafeAnchor != null ? lastSafeAnchor : mc.player.getEyePosition();
        previousFocusAnchor = focusAnchor;
        // Focus the exposed face, not a point inside ore or the roof of a narrow tunnel.
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        double bestScore = -Double.MAX_VALUE;
        Vec3 target = center;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            var neighbor = pos.relative(direction);
            if (!mc.level.hasChunkAt(neighbor)
                    || !mc.level.getBlockState(neighbor).getCollisionShape(mc.level, neighbor).isEmpty()) continue;
            Vec3 offset = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            double score = -look.dot(offset);
            if (score > bestScore) { bestScore = score; target = center.add(offset.scale(.6)); }
        }
        focusTarget = target;
    }
    public static boolean hasFocus() { return focusTarget != null; }
    public static boolean followingEntity() { return followTarget != null; }
    public static void clearFocus() {
        focusTarget = null; focusAnchor = null; previousFocusAnchor = null;
        followTarget = null; followWorld = null;
    }

    public static void focusSelected() {
        Minecraft mc = Minecraft.getInstance();
        if (!isCameraActive(mc)) return;
        var selected = dev.stonebanner.client.control.CitizenSelectionController.selected();
        if (selected.isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("message.stonebanner.focus_no_selection"), true);
            return;
        }
        Entity entity = selected.get();
        var requested = new CameraFollowTarget(entity.getId(), entity.getUUID());
        if (!requested.matches(entity.getId(), entity.getUUID(), entity.isAlive(), mc.level.hasChunkAt(entity.blockPosition()),
                entity.getEyePosition().distanceToSqr(mc.player.getEyePosition()))) {
            mc.player.displayClientMessage(Component.translatable("message.stonebanner.focus_too_far"), true);
            return;
        }
        initializeOrientationIfNeeded(mc.player);
        clearFocus();
        followTarget = requested;
        followWorld = mc.level;
        focusAnchor = lastSafeAnchor != null ? lastSafeAnchor : mc.player.getEyePosition();
        previousFocusAnchor = focusAnchor;
        focusTarget = entity.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0);
        mc.player.displayClientMessage(Component.translatable("message.stonebanner.focus_following", entity.getDisplayName(),
                dev.stonebanner.client.ClientKeyMappings.RECENTER_CAMERA.getTranslatedKeyMessage()), true);
        StoneAndBanner.LOGGER.debug("Camera following NPC {} ({})", entity.getId(), entity.getUUID());
    }

    private static Entity followedEntity(Minecraft mc) {
        if (followTarget == null) return null;
        Entity entity = mc.level == followWorld && mc.level != null ? mc.level.getEntity(followTarget.entityId()) : null;
        if (mc.player == null || entity == null || !followTarget.matches(entity.getId(), entity.getUUID(), entity.isAlive(),
                mc.level.hasChunkAt(entity.blockPosition()), entity.getEyePosition().distanceToSqr(mc.player.getEyePosition()))) {
            recenter();
            return null;
        }
        return entity;
    }


    private RpgCameraController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || trackedPlayer != mc.player) clearFocus();
        Entity following = followedEntity(mc);
        if (following != null) focusTarget = following.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0);
        if (focusTarget != null) {
            previousFocusAnchor = focusAnchor;
            focusAnchor = focusAnchor.lerp(focusTarget, .22);
        }
        ensureInitialized();
        anchorTransition.tick();
        if (tacticalRig.initialized()) tacticalRig.tick();
        if (mc.player != null && mc.level != null && mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen
                && dev.stonebanner.client.control.HeroInputController.commandMode() && tacticalRig.initialized()
                && !dev.stonebanner.client.control.ConstructionPreviewController.active()) {
            double left = (dev.stonebanner.client.control.InputBindings.held(mc.options.keyLeft) ? 1 : 0)
                    - (dev.stonebanner.client.control.InputBindings.held(mc.options.keyRight) ? 1 : 0);
            double forward = (dev.stonebanner.client.control.InputBindings.held(mc.options.keyUp) ? 1 : 0)
                    - (dev.stonebanner.client.control.InputBindings.held(mc.options.keyDown) ? 1 : 0);
            if (mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen screen) {
                var edge = screen.edgePan(); left += edge[0]; forward += edge[1];
            }
            double up = (dev.stonebanner.client.control.InputBindings.held(dev.stonebanner.client.ClientKeyMappings.CAMERA_UP) ? 1 : 0) - (dev.stonebanner.client.control.InputBindings.held(dev.stonebanner.client.ClientKeyMappings.CAMERA_DOWN) ? 1 : 0);
            if (left != 0 || forward != 0 || up != 0) {
                clearFocus();
                tacticalRig.pan(left, forward, up, cameraYaw,
                        (dev.stonebanner.client.control.InputBindings.held(mc.options.keySprint) ? .8 : .35) * ClientConfig.CAMERA_PAN_SENSITIVITY.get());
            }
        }
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
            clearFocus();
            trackedPlayer = null;
            rotatingCamera = false;
            viewObstructed = false;
            tacticalRig.reset(null);
            anchorTransition.reset();
            heroCamera = null;
            lastSafeAnchor = null;
            wasTactical = false;
            cameraDimension = null;
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

        adjustZoom(event.getScrollDelta());
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
        Vec3 eyes = focusedEntity.getEyePosition(partialTick);
        followedEntity(minecraft);
        var dimension = minecraft.level.dimension().location();
        if (cameraDimension != null && !dimension.equals(cameraDimension)) hardReset();
        cameraDimension = dimension;
        boolean tactical = dev.stonebanner.client.control.HeroInputController.commandMode();
        if (wasTactical != tactical) commandsChanged(tactical); // safe fallback if an external source changed the mode
        Vec3 requestedAnchor = tactical && tacticalRig.initialized()
                ? tacticalRig.interpolated(partialTick) : eyes.add(0.0D, ClientConfig.CAMERA_HEIGHT.get(), 0.0D);
        if (focusTarget != null && previousFocusAnchor != null && focusAnchor != null)
            requestedAnchor = previousFocusAnchor.lerp(focusAnchor, partialTick);
        requestedAnchor = anchorTransition.toward(requestedAnchor, partialTick);

        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double radius = CameraCollision.radius(minecraft.gameRenderer.getFov(camera, partialTick, true), aspect);
        if (!Double.isFinite(radius) || radius > 2) {
            viewObstructed = true;
            camera.setPosition(eyes);
            return;
        }
        var context = CollisionContext.of(focusedEntity);
        java.util.function.Function<AABB, List<AABB>> obstacles = bounds -> obstacles(minecraft, context, bounds);
        // Start at the real eyes, never at an unchecked height offset inside a roof.
        boolean freeFocus = tactical || followingEntity() || anchorTransition.active();
        Vec3 sweepStart = freeFocus && lastSafeAnchor != null ? lastSafeAnchor : eyes;
        // A free camera stays in the loaded neighbourhood of the hero, but does not follow their movement.
        if (freeFocus) {
            Vec3 offset = requestedAnchor.subtract(eyes);
            if (offset.length() > CameraFollowTarget.MAX_DISTANCE)
                requestedAnchor = eyes.add(offset.normalize().scale(CameraFollowTarget.MAX_DISTANCE));
        }
        var lift = CameraCollision.sweep(sweepStart, requestedAnchor, radius, obstacles);
        if (freeFocus && lift.blockedStart() && sweepStart != eyes) {
            hardReset();
            lift = CameraCollision.sweep(eyes, eyes.add(0, ClientConfig.CAMERA_HEIGHT.get(), 0), radius, obstacles);
            requestedAnchor = lift.position();
        }
        viewObstructed = lift.blockedStart();
        Vec3 anchor = lift.position();
        lastSafeAnchor = anchor;
        if (tactical && (!tacticalRig.initialized() || focusTarget != null
                || !anchorTransition.active() && anchor.distanceToSqr(requestedAnchor) > 1e-6))
            tacticalRig.reset(anchor);
        Vector3f look = camera.getLookVector();
        Vec3 desired = anchor.subtract(look.x() * currentDistance, look.y() * currentDistance, look.z() * currentDistance);
        var retreat = CameraCollision.sweep(anchor, desired, radius, obstacles);
        viewObstructed |= retreat.blockedStart();
        // Apply collision correction immediately. Only preferred zoom is smoothed; smoothing the safe
        // position would carry the camera through a closing door or a newly entered ceiling.
        camera.setPosition(viewObstructed ? eyes : retreat.position());

    }

    /**
     * Preserves the last collision-validated pivot for an animated return instead of teleporting
     * the camera. Does not alter the actor's position, order, inventory or selected hotbar slot.
     */
    public static void recenter() {
        Minecraft client = Minecraft.getInstance();
        if (lastSafeAnchor != null && client.player != null) {
            Vec3 home = client.player.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0);
            beginSafeTransition(lastSafeAnchor, home);
        }
        clearFocus();
        Minecraft mc = Minecraft.getInstance();
        // In Orders, Home recenters the detached pivot on the hero; a null rig would be
        // reinitialized at the old camera anchor during the next rendering frame.
        if (wasTactical && mc.player != null)
            tacticalRig.reset(mc.player.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0));
        else
            tacticalRig.reset(null);
    }

    private static void beginSafeTransition(Vec3 origin, Vec3 goal) {
        // A distant tactical pivot takes additional ticks, bounding each collision sweep.
        int distanceTicks = (int) Math.ceil(origin.distanceTo(goal) / 5.0D);
        anchorTransition.start(origin, Math.max(ClientConfig.CAMERA_TRANSITION_TICKS.get(), distanceTicks));
    }

    private static void hardReset() {
        clearFocus();
        tacticalRig.reset(null);
        anchorTransition.reset();
        heroCamera = null;
        lastSafeAnchor = null;
    }

    /** Called once when switching between the hero and the detached Orders camera. */
    public static void commandsChanged(boolean tactical) {
        Minecraft mc = Minecraft.getInstance();
        if (!isCameraActive(mc)) {
            wasTactical = tactical;
            return;
        }
        initializeOrientationIfNeeded(mc.player);
        ensureInitialized();
        Vec3 from = lastSafeAnchor != null ? lastSafeAnchor
                : mc.player.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0);
        Vec3 home = mc.player.getEyePosition().add(0, ClientConfig.CAMERA_HEIGHT.get(), 0);
        beginSafeTransition(from, home);
        clearFocus();
        if (tactical) {
            heroCamera = new CameraView(cameraYaw, cameraPitch, targetDistance);
            tacticalRig.reset(from);
            saveCountdown = 0; // RTS zoom/rotation must not overwrite the hero preference.
        } else {
            tacticalRig.reset(null);
            if (heroCamera != null) {
                cameraYaw = heroCamera.yaw();
                cameraPitch = heroCamera.pitch();
                targetDistance = heroCamera.distance();
                saveCountdown = SAVE_DELAY_TICKS;
            }
            heroCamera = null;
        }
        wasTactical = tactical;
    }

    /** Double Home resets the preferred hero viewing angle and distance. */
    public static void resetDefaultView() {
        Minecraft mc = Minecraft.getInstance();
        if (!isCameraActive(mc)) return;
        initializeOrientationIfNeeded(mc.player);
        cameraYaw = mc.player.getYRot();
        cameraPitch = 35.0F;
        targetDistance = 8.0D;
        saveCountdown = SAVE_DELAY_TICKS;
        if (wasTactical && heroCamera != null)
            heroCamera = new CameraView(cameraYaw, cameraPitch, targetDistance);
        recenter();
    }
    public static void panByMouse(double dragX, double dragY) {
        if (!tacticalRig.initialized()) return;
        double length = Math.hypot(dragX, dragY);
        if (length < 1e-6) return;
        clearFocus(); tacticalRig.pan(dragX / length, dragY / length, 0, cameraYaw,
                length * .06 * ClientConfig.CAMERA_PAN_SENSITIVITY.get());
    }

    private static List<AABB> obstacles(Minecraft minecraft, CollisionContext context, AABB bounds) {
        var result = new ArrayList<AABB>();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX) - 1; x <= Mth.floor(bounds.maxX) + 1; x++)
            for (int z = Mth.floor(bounds.minZ) - 1; z <= Mth.floor(bounds.maxZ) + 1; z++) {
                boolean loaded = minecraft.level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) != null;
                for (int y = Mth.floor(bounds.minY) - 1; y <= Mth.floor(bounds.maxY) + 1; y++) {
                    pos.set(x, y, z);
                    // Unloaded chunks act as a barrier, never as transparent empty space.
                    if (!loaded) { result.add(new AABB(pos)); continue; }
                    var shape = minecraft.level.getBlockState(pos).getVisualShape(minecraft.level, pos, context);
                    for (var box : shape.toAabbs()) result.add(box.move(x, y, z));
                }
            }
        return result;
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
        hardReset();
        wasTactical = dev.stonebanner.client.control.HeroInputController.commandMode();
        viewObstructed = false;
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

            cameraYaw = CameraInputSettings.yaw(cameraYaw, yawDelta, ClientConfig.CAMERA_ROTATION_SENSITIVITY.get());
            cameraPitch = CameraInputSettings.pitch(cameraPitch, pitchDelta,
                    ClientConfig.CAMERA_ROTATION_SENSITIVITY.get(), ClientConfig.CAMERA_INVERT_VERTICAL.get());

            player.setYRot(lastPlayerYaw);
            player.setXRot(lastPlayerPitch);
            player.yRotO = lastPlayerYaw;
            player.xRotO = lastPlayerPitch;

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
        cameraYaw = CameraInputSettings.yaw(cameraYaw, -dragX * .45, ClientConfig.CAMERA_ROTATION_SENSITIVITY.get());
        cameraPitch = CameraInputSettings.pitch(cameraPitch, dragY * .45,
                ClientConfig.CAMERA_ROTATION_SENSITIVITY.get(), ClientConfig.CAMERA_INVERT_VERTICAL.get());
        saveCountdown = SAVE_DELAY_TICKS;
    }

    public static void adjustZoom(double scrollDelta) {
        if (scrollDelta == 0.0D) {
            return;
        }
        ensureInitialized();
        targetDistance = CameraInputSettings.zoom(targetDistance, scrollDelta, ClientConfig.CAMERA_ZOOM_SENSITIVITY.get());
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
        if (saveCountdown <= 0 || wasTactical) {
            return;
        }

        saveCountdown--;
        if (saveCountdown == 0) {
            if (Double.compare(ClientConfig.CAMERA_DISTANCE.get(), targetDistance) != 0) {
                ClientConfig.CAMERA_DISTANCE.set(targetDistance);
            }
            if (Double.compare(ClientConfig.CAMERA_PITCH.get(), cameraPitch) != 0) {
                ClientConfig.CAMERA_PITCH.set((double) cameraPitch);
            }
        }
    }
}
