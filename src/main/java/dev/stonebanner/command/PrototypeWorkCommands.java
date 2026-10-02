package dev.stonebanner.command;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.WorkTargetRules;
import dev.stonebanner.citizen.WorkType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary commands for smoke-testing the job system before the Designation UI is fully mature. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class PrototypeWorkCommands {
    private PrototypeWorkCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("stonebanner")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("work")
                                .then(Commands.literal("chop")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> publishForestry(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("mine")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> publishMining(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> publishClearing(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("cancel")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> cancelAt(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("count")
                                        .executes(context -> {
                                            int count = CitizenJobBoard.forLevel(context.getSource().getLevel()).size();
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("Published Citizen jobs: " + count),
                                                    false
                                            );
                                            return count;
                                        }))
                        )
        );
    }

    private static int publishForestry(ServerLevel level, BlockPos target, CommandSourceStack source) {
        if (!WorkTargetRules.isForestryTarget(level, target)) {
            source.sendFailure(Component.literal("Target block is not a log"));
            return 0;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        long id = board.publish(WorkType.FORESTRY, target, level.getGameTime());
        source.sendSuccess(
                () -> Component.literal("Published FORESTRY job #" + id + " at " + target.toShortString()),
                false
        );
        return 1;
    }

    private static int publishMining(ServerLevel level, BlockPos target, CommandSourceStack source) {
        if (!WorkTargetRules.isMiningTarget(level, target)) {
            source.sendFailure(Component.literal("Target block is not a valid mining target"));
            return 0;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        long id = board.publish(
                WorkType.MINING,
                target,
                CitizenSkill.MINING,
                0,
                level.getGameTime()
        );
        source.sendSuccess(
                () -> Component.literal("Published MINING job #" + id + " at " + target.toShortString()),
                false
        );
        return 1;
    }

    private static int publishClearing(ServerLevel level, BlockPos target, CommandSourceStack source) {
        if (!WorkTargetRules.isClearingTarget(level, target)) {
            source.sendFailure(Component.literal("Target block is not a clearing target"));
            return 0;
        }

        CitizenJobBoard board = CitizenJobBoard.forLevel(level);
        long id = board.publish(WorkType.CLEARING, target, level.getGameTime());
        source.sendSuccess(
                () -> Component.literal("Published CLEARING job #" + id + " at " + target.toShortString()),
                false
        );
        return 1;
    }

    private static int cancelAt(ServerLevel level, BlockPos target, CommandSourceStack source) {
        int removed = CitizenJobBoard.forLevel(level).removeAt(target);
        if (removed == 0) {
            source.sendFailure(Component.literal("No Citizen jobs at " + target.toShortString()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Cancelled " + removed + " Citizen job(s) at " + target.toShortString()),
                false
        );
        return removed;
    }
}
