package dev.stonebanner.client.control;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction.Axis;
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

/** A small bounded A* search for the tactical prototype. */
public final class BlockPathfinder {
    private static final int MAX_VISITED_NODES = 4096;
    private static final int MAX_HORIZONTAL_RANGE = 64;
    private static final int MAX_VERTICAL_RANGE = 16;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private BlockPathfinder() {
    }

    public static Optional<List<BlockPos>> findPath(ClientLevel level, BlockPos requestedStart,
                                                     BlockPos requestedGoal) {
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

    public static boolean isWalkable(ClientLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState supportState = level.getBlockState(feet.below());
        VoxelShape feetShape = feetState.getCollisionShape(level, feet);
        boolean water = feetState.getFluidState().is(FluidTags.WATER);
        boolean partialSurface = isTraversablePartialBlock(feetState, feetShape);
        boolean feetClear = feetShape.isEmpty() || water || DoorBlock.isWoodenDoor(feetState) || partialSurface;
        boolean headClear = isPassableBodyState(level, feet.above(), headState);
        boolean extraHeadroom = !partialSurface
                || isPassableBodyState(level, feet.above(2), level.getBlockState(feet.above(2)));
        boolean supported = water
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

    public static Vec3 waypoint(ClientLevel level, BlockPos node) {
        BlockState feetState = level.getBlockState(node);
        VoxelShape feetShape = feetState.getCollisionShape(level, node);
        double y;
        if (isTraversablePartialBlock(feetState, feetShape)) {
            y = node.getY() + feetShape.max(Axis.Y);
        } else if (feetState.getFluidState().is(FluidTags.WATER)) {
            y = node.getY() + 0.1D;
        } else {
            VoxelShape support = level.getBlockState(node.below()).getCollisionShape(level, node.below());
            y = support.isEmpty() ? node.getY() : node.getY() - 1.0D + support.max(Axis.Y);
        }
        return new Vec3(node.getX() + 0.5D, y, node.getZ() + 0.5D);
    }

    public static boolean isClosedWoodenDoor(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN);
    }

    private static List<BlockPos> neighbors(ClientLevel level, BlockPos current, BlockPos start) {
        List<BlockPos> result = new ArrayList<>(4);
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
        return result;
    }

    private static BlockPos firstWalkable(ClientLevel level, BlockPos... candidates) {
        for (BlockPos candidate : candidates) {
            if (isWalkable(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static Optional<BlockPos> findNearbyWalkable(ClientLevel level, BlockPos origin, int radius) {
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

    private static double stepCost(ClientLevel level, BlockPos from, BlockPos to) {
        int vertical = to.getY() - from.getY();
        double base = vertical > 0 ? 1.4D : vertical < 0 ? 1.15D : 1.0D;
        if (level.getFluidState(to).is(FluidTags.WATER)) {
            base += 1.25D;
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
        return state.getBlock() instanceof SlabBlock
                && shape.min(Axis.Y) < 0.01D
                && shape.max(Axis.Y) < 0.99D;
    }

    private static boolean isPassableBodyState(ClientLevel level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty()
                || state.getFluidState().is(FluidTags.WATER)
                || DoorBlock.isWoodenDoor(state);
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
