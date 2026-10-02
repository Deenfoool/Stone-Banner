package dev.stonebanner.designation;

import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkTargetRules;
import dev.stonebanner.citizen.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Converts validated player designations into shared Citizen jobs. */
public final class DesignationService {
    private static final double MAX_DISTANCE_SQR = 128.0D * 128.0D;

    private DesignationService() {
    }

    public static Outcome apply(ServerPlayer player, DesignationType type, BlockPos first, BlockPos second) {
        if (player == null || type == null || first == null || second == null) {
            return new Outcome(Status.REJECTED, 0);
        }

        if (!withinRange(player, first) || !withinRange(player, second)) {
            return new Outcome(Status.REJECTED, 0);
        }
        if (!DesignationLimits.isAllowed(first, second)) {
            return new Outcome(Status.TOO_LARGE, 0);
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());

        ServerLevel level = player.serverLevel();
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);

        if (type == DesignationType.EXCAVATE) {
            int targets = ExcavationPlanData.forLevel(level).createVertical(level, first, second);
            return targets == 0
                    ? new Outcome(Status.NO_TARGETS, 0)
                    : new Outcome(Status.PLANNED, targets);
        }

        int affected = 0;
        if (type == DesignationType.CANCEL) {
            affected += ExcavationPlanData.forLevel(level).cancelIntersecting(level, first, second);
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    if (type == DesignationType.CANCEL) {
                        affected += board.removeAt(pos);
                        continue;
                    }

                    WorkType workType = type.workType();
                    if (workType == null || !WorkTargetRules.isValid(workType, level, pos)) {
                        continue;
                    }

                    int before = board.size();
                    if (workType == WorkType.MINING) {
                        board.publish(workType, pos, CitizenSkill.MINING, 0, level.getGameTime());
                    } else {
                        board.publish(workType, pos, level.getGameTime());
                    }
                    if (board.size() > before) {
                        affected++;
                    }
                }
            }
        }

        return affected == 0
                ? new Outcome(Status.NO_TARGETS, 0)
                : new Outcome(Status.APPLIED, affected);
    }

    private static boolean withinRange(ServerPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        return player.distanceToSqr(center.x, center.y, center.z) <= MAX_DISTANCE_SQR;
    }

    public record Outcome(Status status, int affected) {
    }

    public enum Status {
        APPLIED,
        PLANNED,
        NO_TARGETS,
        TOO_LARGE,
        REJECTED
    }
}
