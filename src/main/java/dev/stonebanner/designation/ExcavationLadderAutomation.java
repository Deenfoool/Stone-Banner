package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Creates and validates real ladder support work for narrow vertical excavation shafts. */
public final class ExcavationLadderAutomation {
    private ExcavationLadderAutomation() {
    }

    /** Called after a mining target was physically removed while the plan still points at the current slice. */
    public static void onMiningCompleted(ServerLevel level, ExcavationPlanData plans, BlockPos minedTarget) {
        if (level == null || plans == null || minedTarget == null) {
            return;
        }
        Optional<ExcavationPlanData.PlanView> planResult = plans.activePlanFor(minedTarget);
        if (planResult.isEmpty()) {
            return;
        }

        ExcavationPlanData.PlanView plan = planResult.get();
        if (plan.modeCode() != 0 || ExcavationEgressSafety.rampSupportAtY(plan, minedTarget.getY()) != null) {
            return;
        }

        BlockPos ladderPos = ExcavationEgressSafety.ladderAccessAtY(plan, minedTarget.getY());
        if (!level.hasChunkAt(ladderPos)
                || level.getBlockState(ladderPos).is(Blocks.LADDER)
                || !level.getBlockState(ladderPos).isAir()) {
            return;
        }

        findFacing(level, plan, ladderPos).ifPresent(facing ->
                ExcavationLadderTaskData.forLevel(level).publish(level, ladderPos, facing)
        );
    }

    public static Optional<BlockState> placementState(ServerLevel level,
                                                      ExcavationLadderTaskData.LadderTask task) {
        if (level == null || task == null || !level.hasChunkAt(task.target())) {
            return Optional.empty();
        }
        if (level.getBlockState(task.target()).is(Blocks.LADDER)) {
            return Optional.of(level.getBlockState(task.target()));
        }
        if (!level.getBlockState(task.target()).isAir()) {
            return Optional.empty();
        }
        BlockState state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, task.facing());
        return state.canSurvive(level, task.target()) ? Optional.of(state) : Optional.empty();
    }

    private static Optional<Direction> findFacing(ServerLevel level,
                                                  ExcavationPlanData.PlanView plan,
                                                  BlockPos ladderPos) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos support = ladderPos.relative(facing.getOpposite());
            if (insidePlan(plan, support)) {
                continue;
            }
            BlockState state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            if (state.canSurvive(level, ladderPos)) {
                return Optional.of(facing);
            }
        }
        return Optional.empty();
    }

    private static boolean insidePlan(ExcavationPlanData.PlanView plan, BlockPos pos) {
        return pos.getX() >= plan.minX() && pos.getX() <= plan.maxX()
                && pos.getY() >= plan.minY() && pos.getY() <= plan.maxY()
                && pos.getZ() >= plan.minZ() && pos.getZ() <= plan.maxZ();
    }
}
