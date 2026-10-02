package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Shared validation for physical work targets used by designations, commands and workers. */
public final class WorkTargetRules {
    private WorkTargetRules() {
    }

    public static boolean isForestryTarget(LevelReader level, BlockPos pos) {
        return level != null && pos != null && level.getBlockState(pos).is(BlockTags.LOGS);
    }

    public static boolean isMiningTarget(LevelReader level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.is(BlockTags.LOGS)
                && !isClearingState(state)
                && !state.getCollisionShape(level, pos).isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static boolean isClearingTarget(LevelReader level, BlockPos pos) {
        return level != null && pos != null && isClearingState(level.getBlockState(pos));
    }

    public static boolean isValid(WorkType workType, LevelReader level, BlockPos pos) {
        if (workType == null) {
            return false;
        }
        return switch (workType) {
            case FORESTRY -> isForestryTarget(level, pos);
            case MINING -> isMiningTarget(level, pos);
            case CLEARING -> isClearingTarget(level, pos);
            default -> false;
        };
    }

    private static boolean isClearingState(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW);
    }
}
