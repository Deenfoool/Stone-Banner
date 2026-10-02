package dev.stonebanner.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.citizen.BodyPart;
import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.CitizenParticipation;
import dev.stonebanner.citizen.InjuryState;
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
    private static final SimpleCommandExceptionType INVALID_BODY_PART =
            new SimpleCommandExceptionType(Component.literal("Unknown body part"));
    private static final SimpleCommandExceptionType INVALID_INJURY =
            new SimpleCommandExceptionType(Component.literal("Unknown injury state"));
    private static final SimpleCommandExceptionType INVALID_PARTICIPATION =
            new SimpleCommandExceptionType(Component.literal("Unknown participation: local_helper, companion, settler"));

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
                                                        )))))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(context -> stop(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "npc")
                                                ))))
                                .then(Commands.literal("status")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(context -> status(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "npc")
                                                ))))
                                .then(Commands.literal("needs")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.literal("hunger")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> setNeed(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        "hunger",
                                                                        IntegerArgumentType.getInteger(context, "value")
                                                                ))))
                                                .then(Commands.literal("fatigue")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> setNeed(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        "fatigue",
                                                                        IntegerArgumentType.getInteger(context, "value")
                                                                ))))
                                                .then(Commands.literal("danger")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> setNeed(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        "danger",
                                                                        IntegerArgumentType.getInteger(context, "value")
                                                                ))))))
                                .then(Commands.literal("injury")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("part", StringArgumentType.word())
                                                        .then(Commands.argument("state", StringArgumentType.word())
                                                                .executes(context -> setInjury(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        StringArgumentType.getString(context, "part"),
                                                                        StringArgumentType.getString(context, "state")
                                                                ))))))
                                .then(Commands.literal("home")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 16))
                                                                .executes(context -> setHome(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                        IntegerArgumentType.getInteger(context, "radiusChunks")
                                                                ))))))
                                .then(Commands.literal("participation")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .executes(context -> setParticipation(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "npc"),
                                                                StringArgumentType.getString(context, "mode")
                                                        )))))
                        )
        );
    }

    private static int move(CommandSourceStack source, Entity entity, BlockPos target) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        boolean accepted = npc.issueCommand(new ActorCommand.MoveTo(target));
        source.sendSuccess(
                () -> Component.literal(accepted ? "Human NPC moving to " + target.toShortString()
                        : "Human NPC rejected the route (unreachable or outside home territory)"),
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

    private static int status(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        CitizenNeeds needs = npc.citizenData().needs();
        String home = npc.citizenData().home().hasHome()
                ? npc.citizenData().home().homePos().toShortString() + "/" + npc.citizenData().home().radiusChunks() + "ch"
                : "none";
        source.sendSuccess(
                () -> Component.literal(
                        "state=" + npc.brainState().serializedName()
                                + " profession=" + npc.citizenData().profession().serializedName()
                                + " participation=" + npc.citizenData().participation().serializedName()
                                + " home=" + home
                                + " hunger=" + Math.round(needs.hunger())
                                + " fatigue=" + Math.round(needs.fatigue())
                                + " danger=" + Math.round(needs.danger())
                                + " move=" + String.format(java.util.Locale.ROOT, "%.2f", npc.citizenData().health().movementMultiplier())
                                + " work=" + String.format(java.util.Locale.ROOT, "%.2f", npc.citizenData().health().workEfficiencyMultiplier())
                ),
                false
        );
        return 1;
    }

    private static int setNeed(CommandSourceStack source, Entity entity, String type, int value)
            throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        CitizenNeeds needs = npc.citizenData().needs();
        switch (type) {
            case "hunger" -> needs.setHunger(value);
            case "fatigue" -> needs.setFatigue(value);
            case "danger" -> needs.setDanger(value);
            default -> throw new IllegalArgumentException("Unknown need type: " + type);
        }
        source.sendSuccess(() -> Component.literal("Set " + type + " to " + value), false);
        return 1;
    }

    private static int setInjury(CommandSourceStack source, Entity entity, String partName, String stateName)
            throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        BodyPart part = parseBodyPart(partName);
        InjuryState state = parseInjuryState(stateName);
        npc.citizenData().health().setInjury(part, state);
        source.sendSuccess(
                () -> Component.literal("Set " + part.serializedName() + " injury to " + state.serializedName()),
                false
        );
        return 1;
    }

    private static int setHome(CommandSourceStack source, Entity entity, BlockPos position, int radiusChunks)
            throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        npc.citizenData().home().assign("debug", position, radiusChunks);
        source.sendSuccess(
                () -> Component.literal("Home set to " + position.toShortString() + " radius=" + radiusChunks + " chunks"),
                false
        );
        return 1;
    }

    private static int setParticipation(CommandSourceStack source, Entity entity, String mode)
            throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        CitizenParticipation participation = parseParticipation(mode);
        npc.citizenData().setParticipation(participation);
        source.sendSuccess(
                () -> Component.literal("Participation set to " + participation.serializedName()),
                false
        );
        return 1;
    }

    private static BodyPart parseBodyPart(String value) throws CommandSyntaxException {
        for (BodyPart part : BodyPart.values()) {
            if (part.serializedName().equalsIgnoreCase(value)) {
                return part;
            }
        }
        throw INVALID_BODY_PART.create();
    }

    private static InjuryState parseInjuryState(String value) throws CommandSyntaxException {
        for (InjuryState state : InjuryState.values()) {
            if (state.serializedName().equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw INVALID_INJURY.create();
    }

    private static CitizenParticipation parseParticipation(String value) throws CommandSyntaxException {
        for (CitizenParticipation participation : CitizenParticipation.values()) {
            if (participation.serializedName().equalsIgnoreCase(value)) {
                return participation;
            }
        }
        throw INVALID_PARTICIPATION.create();
    }

    private static HumanNpcEntity requireHumanNpc(Entity entity) throws CommandSyntaxException {
        if (entity instanceof HumanNpcEntity npc) {
            return npc;
        }
        throw NOT_HUMAN_NPC.create();
    }
}
