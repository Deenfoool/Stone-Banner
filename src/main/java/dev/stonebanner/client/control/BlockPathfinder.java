package dev.stonebanner.client.control;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Temporary client compatibility facade.
 *
 * The actual terrain/path query now lives in the common navigation package so both the
 * player controller and future server-side Citizen AI can use the same rules. Existing
 * client call sites stay untouched until the player-control cleanup is finished.
 */
@Deprecated(forRemoval = true)
public final class BlockPathfinder {
    private BlockPathfinder() {
    }

    public static Optional<List<BlockPos>> findPath(ClientLevel level, BlockPos requestedStart,
                                                     BlockPos requestedGoal) {
        return dev.stonebanner.navigation.BlockPathfinder.findPath(level, requestedStart, requestedGoal);
    }

    public static boolean isWalkable(ClientLevel level, BlockPos feet) {
        return dev.stonebanner.navigation.BlockPathfinder.isWalkable(level, feet);
    }

    public static Vec3 waypoint(ClientLevel level, BlockPos node) {
        return dev.stonebanner.navigation.BlockPathfinder.waypoint(level, node);
    }

    public static boolean isClosedWoodenDoor(ClientLevel level, BlockPos pos) {
        return dev.stonebanner.navigation.BlockPathfinder.isClosedWoodenDoor(level, pos);
    }

    public static boolean isClimbable(ClientLevel level, BlockPos pos) {
        return dev.stonebanner.navigation.BlockPathfinder.isClimbable(level, pos);
    }

    public static Optional<Direction> climbDirection(ClientLevel level, BlockPos pos) {
        return dev.stonebanner.navigation.BlockPathfinder.climbDirection(level, pos);
    }
}
