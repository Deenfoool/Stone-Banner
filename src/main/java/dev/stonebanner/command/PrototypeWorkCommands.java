package dev.stonebanner.command;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.WorkType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary commands for smoke-testing the job system before the Designation UI exists. */
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

    private static int publishForestry(ServerLevel level, BlockPos target,
                                       net.minecraft.commands.CommandSourceStack source) {
        if (!level.getBlockState(target).is(BlockTags.LOGS)) {
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
}
