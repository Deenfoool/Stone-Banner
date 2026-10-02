package dev.stonebanner.command;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Transport-neutral actor command model shared by player control and Citizen AI.
 *
 * Commands describe intent only. Player and NPC executors own movement, networking and side effects.
 */
public sealed interface ActorCommand permits ActorCommand.MoveTo, ActorCommand.FollowEntity,
        ActorCommand.EntityAction, ActorCommand.Stop {
    CommandType type();

    enum CommandType {
        MOVE_TO,
        FOLLOW_ENTITY,
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

    record FollowEntity(int entityId, double preferredDistance) implements ActorCommand {
        public FollowEntity {
            if (preferredDistance < 1.0D) {
                preferredDistance = 1.0D;
            }
        }

        @Override
        public CommandType type() {
            return CommandType.FOLLOW_ENTITY;
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
