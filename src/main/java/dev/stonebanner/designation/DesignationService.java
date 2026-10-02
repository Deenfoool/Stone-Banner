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
    public static final int MAX_BLOCKS_PER_REQUEST = 4096;
    public static final int MAX_AXIS_LENGTH = 64;
    private static final double MAX_DISTANCE_SQR = 128.0D * 128.0D;

    private DesignationService() {
    }

    public static Result apply(ServerPlayer player, DesignationType type, BlockPos first, BlockPos second) {
        if (player == null || type == null || first == null || second == null) {
            return Result.REJECTED;
        }

        if (!withinRange(player, first) || !withinRange(player, second)) {
            return Result.REJECTED;
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeX > MAX_AXIS_LENGTH || sizeY > MAX_AXIS_LENGTH || sizeZ > MAX_AXIS_LENGTH
                || volume > MAX_BLOCKS_PER_REQUEST) {
            return Result.TOO_LARGE;
        }

        ServerLevel level = player.serverLevel();
        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        int affected = 0;

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

                    if (workType == WorkType.MINING) {
                        board.publish(workType, pos, CitizenSkill.MINING, 0, level.getGameTime());
                    } else {
                        board.publish(workType, pos, level.getGameTime());
                    }
                    affected++;
                }
            }
        }

        return affected == 0 ? Result.NO_TARGETS : Result.APPLIED;
    }

    private static boolean withinRange(ServerPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        return player.distanceToSqr(center.x, center.y, center.z) <= MAX_DISTANCE_SQR;
    }

    public enum Result {
        APPLIED,
        NO_TARGETS,
        TOO_LARGE,
        REJECTED
    }
}
