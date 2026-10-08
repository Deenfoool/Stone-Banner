package dev.stonebanner.client.control;

import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.control.CursorDwellTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Optional;

/** Client-only pointer hints and 'command sent' feedback: never treated as a server acknowledgement. */
public final class ContextFeedbackController {
    public enum Kind { MOVE, RUN, INTERACT, MINE, ATTACK, DENIED, ORDER }
    public record Pulse(Vec3 location, Kind kind, float opacity) {}
    public record ScreenNote(int x, int y, Component label, int color, float opacity) {}

    private static final CursorDwellTracker dwell = new CursorDwellTracker();
    private static ClientLevel trackedWorld;
    private static long startedMillis;
    private static Vec3 sentLocation;
    private static Kind sentKind;
    private static Component sentLabel;
    private static int noteX, noteY;

    private ContextFeedbackController() {}

    private static void ensureWorld() {
        var world = Minecraft.getInstance().level;
        if (world != trackedWorld) {
            trackedWorld = world;
            reset();
        }
    }

    public static void observe(HitResult hit, double x, double y, boolean blocked, long now) {
        ensureWorld();
        if (!ClientConfig.SHOW_CONTEXT_HINTS.get() || blocked || hit == null) {
            dwell.reset();
            return;
        }
        String key = hit instanceof EntityHitResult entity
                ? "entity:" + entity.getEntity().getUUID()
                : hit instanceof BlockHitResult block ? "block:" + block.getBlockPos().asLong() : null;
        dwell.observe(key, x, y, now, 7.0D);
    }

    public static boolean hintReady(long now) {
        return ClientConfig.SHOW_CONTEXT_HINTS.get()
                && dwell.ready(now, ClientConfig.CONTEXT_HINT_DELAY_MS.get());
    }

    /** Only invoked after a local action was issued or an actual route was rejected. */
    public static void submitted(HitResult hit, Kind kind, double x, double y, Component label) {
        ensureWorld();
        dwell.reset();
        if (!ClientConfig.SHOW_COMMAND_FEEDBACK.get() || hit == null) return;
        sentLocation = hit.getLocation();
        sentKind = kind;
        sentLabel = label;
        noteX = (int) x;
        noteY = (int) y;
        startedMillis = net.minecraft.Util.getMillis();
    }

    public static Optional<Pulse> pulse(long now) {
        ensureWorld();
        if (!ClientConfig.SHOW_COMMAND_FEEDBACK.get() || sentLocation == null) return Optional.empty();
        float opacity = fade(now, startedMillis, 720);
        return opacity > 0 ? Optional.of(new Pulse(sentLocation, sentKind, opacity)) : Optional.empty();
    }

    public static Optional<ScreenNote> note(long now) {
        ensureWorld();
        if (!ClientConfig.SHOW_COMMAND_FEEDBACK.get() || sentLabel == null) return Optional.empty();
        float opacity = fade(now, startedMillis, 900);
        int color = sentKind == Kind.DENIED ? 0xFFFF7777 : 0xFFE4D29A;
        return opacity > 0 ? Optional.of(new ScreenNote(noteX, noteY, sentLabel, color, opacity)) : Optional.empty();
    }

    static float fade(long now, long start, int durationMs) {
        if (now < start || now - start >= durationMs) return 0;
        return Math.max(0f, 1f - (float)(now - start) / durationMs);
    }

    public static void reset() {
        dwell.reset();
        sentLocation = null;
        sentKind = null;
        sentLabel = null;
        startedMillis = 0;
    }
}
