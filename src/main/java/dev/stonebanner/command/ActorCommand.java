package dev.stonebanner.command;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Transport-neutral actor command model shared by player control and future Citizen AI.
 *
 * The command only describes intent. Execution is owned by the actor-specific controller
 * on the appropriate side (client player controller today, server-side NPC controller later).
 */
public sealed interface ActorCommand permits ActorCommand.MoveTo, ActorCommand.EntityAction, ActorCommand.Stop {
    CommandType type();

    enum CommandType {
        MOVE_TO,
        ENTITY_ACTION,
        STOP
    }

    enum EntityActionType {
        SELECT,
        ATTACK,
        INTERACT
    }

    record MoveTo(BlockPos target) implements ActorCommand {
        public MoveTo {
            target = Objects.requireNonNull(target, "target").immutable();
        }

        @Override
        public CommandType type() {
            return CommandType.MOVE_TO;
        }
    }

    record EntityAction(int entityId, EntityActionType action) implements ActorCommand {
        public EntityAction {
            action = Objects.requireNonNull(action, "action");
        }

        @Override
        public CommandType type() {
            return CommandType.ENTITY_ACTION;
        }
    }

    record Stop() implements ActorCommand {
        @Override
        public CommandType type() {
            return CommandType.STOP;
        }
    }
}
