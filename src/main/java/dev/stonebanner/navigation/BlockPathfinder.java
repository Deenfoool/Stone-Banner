package dev.stonebanner.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded A* path query shared by client player control and future server-side NPC navigation.
 *
 * This class only reads world state. It deliberately depends on common {@link Level}, not ClientLevel,
 * so Citizen AI can reuse the same terrain classification without importing client classes.
 */
public final class BlockPathfinder {
    private static final int MAX_VISITED_NODES = 4096;
    private static final int MAX_HORIZONTAL_RANGE = 64;
    private static final int MAX_VERTICAL_RANGE = 16;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private BlockPathfinder() {
    }

    public static Optional<List<BlockPos>> findPath(Level level, BlockPos requestedStart, BlockPos requestedGoal) {
        Optional<BlockPos> startResult = findNearbyWalkable(level, requestedStart, 1);
        Optional<BlockPos> goalResult = findNearbyWalkable(level, requestedGoal, 3);
        if (startResult.isEmpty() || goalResult.isEmpty()) {
            return Optional.empty();
        }

        BlockPos start = startResult.get();
        BlockPos goal = goalResult.get();
        if (start.equals(goal)) {
            return Optional.of(List.of());
        }
        if (horizontalDistance(start, goal) > MAX_HORIZONTAL_RANGE) {
            return Optional.empty();
        }

        PriorityQueue<SearchNode> open = new PriorityQueue<>();
        Map<Long, Double> costs = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        costs.put(start.asLong(), 0.0D);
        open.add(new SearchNode(start, heuristic(start, goal)));

        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_VISITED_NODES) {
            SearchNode currentNode = open.poll();
            BlockPos current = currentNode.position();
            long currentKey = current.asLong();
            if (!closed.add(currentKey)) {
                continue;
            }
            if (current.equals(goal)) {
                return Optional.of(reconstructPath(start, goal, parents));
            }

            for (BlockPos neighbor : neighbors(level, current, start)) {
                long neighborKey = neighbor.asLong();
                if (closed.contains(neighborKey)) {
                    continue;
                }

                double newCost = costs.get(currentKey) + stepCost(level, current, neighbor);
                if (newCost >= costs.getOrDefault(neighborKey, Double.POSITIVE_INFINITY)) {
                    continue;
                }

                parents.put(neighborKey, currentKey);
                costs.put(neighborKey, newCost);
                open.add(new SearchNode(neighbor, newCost + heuristic(neighbor, goal)));
            }
        }
        return Optional.empty();
    }

    public static boolean isWalkable(Level level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState supportState = level.getBlockState(feet.below());
        VoxelShape feetShape = feetState.getCollisionShape(level, feet);
        boolean water = feetState.getFluidState().is(FluidTags.WATER) && feetShape.isEmpty();
        boolean climbable = isClimbable(feetState);
        boolean partialSurface = isTraversablePartialBlock(feetState, feetShape);
        boolean feetClear = feetShape.isEmpty()
                || water
                || DoorBlock.isWoodenDoor(feetState)
                || isOpenTrapdoor(feetState)
                || climbable
                || partialSurface;
        boolean headClear = isPassableBodyState(level, feet.above(), headState);
        boolean extraHeadroom = !partialSurface
                || isPassableBodyState(level, feet.above(2), level.getBlockState(feet.above(2)));
        boolean supported = water
                || climbable
                || partialSurface
                || !supportState.getCollisionShape(level, feet.below()).isEmpty();
        boolean bodyClear = feetClear && headClear && extraHeadroom;
        boolean dangerous = supportState.is(Blocks.MAGMA_BLOCK)
                || supportState.is(Blocks.CAMPFIRE)
                || supportState.is(Blocks.SOUL_CAMPFIRE)
                || feetState.getFluidState().is(FluidTags.LAVA)
                || headState.getFluidState().is(FluidTags.LAVA);
        return bodyClear && supported && !dangerous;
    }

    public static Vec3 waypoint(Level level, BlockPos node) {
        BlockState feetState = level.getBlockState(node);
        VoxelShape feetShape = feetState.getCollisionShape(level, node);
        double y;
        if (isTraversablePartialBlock(feetState, feetShape)) {
            y = node.getY() + feetShape.max(Axis.Y);
        } else if (isClimbable(feetState)) {
            y = node.getY() + 0.1D;
        } else if (feetState.getFluidState().is(FluidTags.WATER) && feetShape.isEmpty()) {
            y = node.getY() + 0.1D;
        } else {
            VoxelShape support = level.getBlockState(node.below()).getCollisionShape(level, node.below());
            y = support.isEmpty() ? node.getY() : node.getY() - 1.0D + support.max(Axis.Y);
        }
        return new Vec3(node.getX() + 0.5D, y, node.getZ() + 0.5D);
    }

    public static boolean isClosedWoodenDoor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN);
    }

    public static boolean isClimbable(Level level, BlockPos pos) {
        return isClimbable(level.getBlockState(pos));
    }

    /** Direction to press while climbing: toward the block supporting the ladder. */
    public static Optional<Direction> climbDirection(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(net.minecraft.world.level.block.LadderBlock.FACING)) {
            return Optional.of(state.getValue(net.minecraft.world.level.block.LadderBlock.FACING).getOpposite());
        }
        return Optional.empty();
    }

    private static List<BlockPos> neighbors(Level level, BlockPos current, BlockPos start) {
        List<BlockPos> result = new ArrayList<>(6);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos horizontal = current.relative(direction);
            BlockPos neighbor = firstWalkable(level, horizontal, horizontal.above(), horizontal.below());
            if (neighbor == null || Math.abs(neighbor.getY() - start.getY()) > MAX_VERTICAL_RANGE) {
                continue;
            }
            if (neighbor.getY() > current.getY()
                    && !level.getBlockState(current.above(2)).getCollisionShape(level, current.above(2)).isEmpty()) {
                continue;
            }
            result.add(neighbor);
        }
        addClimbNeighbor(level, current, current.above(), start, result);
        addClimbNeighbor(level, current, current.below(), start, result);
        return result;
    }

    private static void addClimbNeighbor(Level level, BlockPos current, BlockPos candidate,
                                         BlockPos start, List<BlockPos> result) {
        if ((!isClimbable(level, current) && !isClimbable(level, candidate))
                || Math.abs(candidate.getY() - start.getY()) > MAX_VERTICAL_RANGE
                || !isWalkable(level, candidate)) {
            return;
        }
        result.add(candidate.immutable());
    }

    private static BlockPos firstWalkable(Level level, BlockPos... candidates) {
        for (BlockPos candidate : candidates) {
            if (isWalkable(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static Optional<BlockPos> findNearbyWalkable(Level level, BlockPos origin, int radius) {
        for (int horizontal = 0; horizontal <= radius; horizontal++) {
            for (int x = -horizontal; x <= horizontal; x++) {
                int zDistance = horizontal - Math.abs(x);
                for (int zSign : zDistance == 0 ? new int[]{0} : new int[]{-zDistance, zDistance}) {
                    for (int yOffset : new int[]{0, 1, -1, 2, -2}) {
                        BlockPos candidate = origin.offset(x, yOffset, zSign);
                        if (isWalkable(level, candidate)) {
                            return Optional.of(candidate.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static List<BlockPos> reconstructPath(BlockPos start, BlockPos goal, Map<Long, Long> parents) {
        List<BlockPos> reversed = new ArrayList<>();
        long cursor = goal.asLong();
        while (cursor != start.asLong()) {
            reversed.add(BlockPos.of(cursor));
            Long parent = parents.get(cursor);
            if (parent == null) {
                return List.of();
            }
            cursor = parent;
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getZ() - to.getZ())
                + Math.abs(from.getY() - to.getY()) * 0.75D;
    }

    private static double stepCost(Level level, BlockPos from, BlockPos to) {
        int vertical = to.getY() - from.getY();
        double base = vertical > 0 ? 1.4D : vertical < 0 ? 1.15D : 1.0D;
        if (level.getFluidState(to).is(FluidTags.WATER)
                && level.getBlockState(to).getCollisionShape(level, to).isEmpty()) {
            base += 1.25D;
        }
        if (isClimbable(level, to) || isClimbable(level, from)) {
            base += 0.65D;
        }
        if (DoorBlock.isWoodenDoor(level.getBlockState(to))) {
            base += 0.35D;
        }
        return base;
    }

    private static boolean isTraversablePartialBlock(BlockState state, VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof StairBlock) {
            return true;
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.OPEN)
                    && state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
        }
        return state.getBlock() instanceof SlabBlock
                && shape.min(Axis.Y) < 0.01D
                && shape.max(Axis.Y) < 0.99D;
    }

    private static boolean isPassableBodyState(Level level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty()
                || DoorBlock.isWoodenDoor(state)
                || isOpenTrapdoor(state)
                || isClimbable(state);
    }

    private static boolean isOpenTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
    }

    private static boolean isClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE);
    }

    private static double horizontalDistance(BlockPos from, BlockPos to) {
        return Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ());
    }

    private record SearchNode(BlockPos position, double score) implements Comparable<SearchNode> {
        @Override
        public int compareTo(SearchNode other) {
            return Double.compare(score, other.score);
        }
    }
}
