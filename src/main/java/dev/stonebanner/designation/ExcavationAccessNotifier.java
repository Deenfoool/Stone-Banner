package dev.stonebanner.designation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime-only transition tracker for excavation access problems.
 *
 * <p>Reconciliation runs repeatedly while a plan is paused, so notifications are emitted only when the
 * status changes. Returning to READY reports recovery once and clears the remembered problem, allowing a
 * later obstruction to notify again.</p>
 */
final class ExcavationAccessNotifier {
    private static final Map<ServerLevel, Map<PlanKey, ExcavationAccessStatus>> LAST_PROBLEMS = new WeakHashMap<>();

    private ExcavationAccessNotifier() {
    }

    static void update(ServerLevel level, ExcavationPlanData.PlanView plan, ExcavationAccessStatus status) {
        if (level == null || plan == null || status == null) {
            return;
        }

        PlanKey key = PlanKey.from(plan);
        Map<PlanKey, ExcavationAccessStatus> levelProblems = LAST_PROBLEMS.computeIfAbsent(level, ignored -> new HashMap<>());

        if (status == ExcavationAccessStatus.READY) {
            ExcavationAccessStatus previous = levelProblems.remove(key);
            if (previous != null) {
                broadcast(level, Component.translatable(status.translationKey()));
            }
            return;
        }

        ExcavationAccessStatus previous = levelProblems.put(key, status);
        if (previous == status) {
            return;
        }
        broadcast(level, Component.translatable(status.translationKey()));
    }

    static void clear(ServerLevel level, ExcavationPlanData.PlanView plan) {
        if (level == null || plan == null) {
            return;
        }
        Map<PlanKey, ExcavationAccessStatus> levelProblems = LAST_PROBLEMS.get(level);
        if (levelProblems != null) {
            levelProblems.remove(PlanKey.from(plan));
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        level.players().forEach(player -> player.displayClientMessage(message, true));
    }

    private record PlanKey(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int modeCode,
            int step
    ) {
        private static PlanKey from(ExcavationPlanData.PlanView plan) {
            return new PlanKey(
                    plan.minX(), plan.minY(), plan.minZ(),
                    plan.maxX(), plan.maxY(), plan.maxZ(),
                    plan.modeCode(), plan.step()
            );
        }
    }
}
