package dev.stonebanner.command;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.storage.StorageData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side diagnostics for the real-container storage foundation. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class StorageCommands {
    private StorageCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("stonebanner")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("storage")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> add(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> remove(
                                                        context.getSource().getLevel(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        context.getSource()
                                                ))))
                                .then(Commands.literal("status")
                                        .executes(context -> status(
                                                context.getSource().getLevel(),
                                                context.getSource()
                                        )))
                )
        );
    }

    private static int add(ServerLevel level, BlockPos pos, net.minecraft.commands.CommandSourceStack source) {
        StorageData.RegisterResult result = StorageData.forLevel(level).register(level, pos);
        source.sendSuccess(() -> Component.literal("Storage " + result.name().toLowerCase() + ": " + pos.toShortString()), false);
        return result == StorageData.RegisterResult.ADDED || result == StorageData.RegisterResult.ALREADY_REGISTERED ? 1 : 0;
    }

    private static int remove(ServerLevel level, BlockPos pos, net.minecraft.commands.CommandSourceStack source) {
        boolean removed = StorageData.forLevel(level).unregister(pos);
        source.sendSuccess(() -> Component.literal(
                (removed ? "Storage removed: " : "Storage was not registered: ") + pos.toShortString()
        ), false);
        return removed ? 1 : 0;
    }

    private static int status(ServerLevel level, net.minecraft.commands.CommandSourceStack source) {
        StorageData storage = StorageData.forLevel(level);
        int totalItems = storage.count(level, stack -> true);
        int ladders = storage.countItem(level, Items.LADDER);
        source.sendSuccess(() -> Component.literal(
                "Storage containers: " + storage.registeredCount()
                        + ", loaded items: " + totalItems
                        + ", ladders: " + ladders
        ), false);
        return storage.registeredCount();
    }
}
