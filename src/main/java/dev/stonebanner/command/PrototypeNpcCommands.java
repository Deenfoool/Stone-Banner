package dev.stonebanner.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary server-side commands used to verify the Citizen foundation in dev worlds. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class PrototypeNpcCommands {
    private static final SimpleCommandExceptionType NOT_HUMAN_NPC =
            new SimpleCommandExceptionType(Component.literal("Target is not a Stone & Banner Human NPC"));

    private PrototypeNpcCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("stonebanner")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("npc")
                                .then(Commands.literal("move")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(context -> move(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "npc"),
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                                                        )))))
                                .then(Commands.literal("follow")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("target", EntityArgument.entity())
                                                        .executes(context -> follow(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "npc"),
                                                                EntityArgument.getEntity(context, "target")
                                                        ))))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(context -> stop(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "npc")
                                                ))))
                        )
        );
    }

    private static int move(CommandSourceStack source, Entity entity, BlockPos target) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        boolean accepted = npc.issueCommand(new ActorCommand.MoveTo(target));
        source.sendSuccess(
                () -> Component.literal(accepted ? "Human NPC moving to " + target.toShortString()
                        : "Human NPC could not plan a route"),
                false
        );
        return accepted ? 1 : 0;
    }

    private static int follow(CommandSourceStack source, Entity entity, Entity target) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        boolean accepted = npc.issueCommand(new ActorCommand.FollowEntity(target.getId(), 2.5D));
        source.sendSuccess(
                () -> Component.literal(accepted ? "Human NPC following " + target.getDisplayName().getString()
                        : "Human NPC could not follow that target"),
                false
        );
        return accepted ? 1 : 0;
    }

    private static int stop(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        npc.issueCommand(new ActorCommand.Stop());
        source.sendSuccess(() -> Component.literal("Human NPC stopped"), false);
        return 1;
    }

    private static HumanNpcEntity requireHumanNpc(Entity entity) throws CommandSyntaxException {
        if (entity instanceof HumanNpcEntity npc) {
            return npc;
        }
        throw NOT_HUMAN_NPC.create();
    }
}
