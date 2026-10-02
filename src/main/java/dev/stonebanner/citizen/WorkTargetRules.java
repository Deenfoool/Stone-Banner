package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
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
                && !state.getCollisionShape(level, pos).isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static boolean isValid(WorkType workType, LevelReader level, BlockPos pos) {
        if (workType == null) {
            return false;
        }
        return switch (workType) {
            case FORESTRY -> isForestryTarget(level, pos);
            case MINING -> isMiningTarget(level, pos);
            default -> false;
        };
    }
}
