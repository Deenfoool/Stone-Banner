package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorCommandTest {
    @Test
    void moveCommandKeepsAnImmutableTarget() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(4, 70, -3);
        ActorCommand.MoveTo command = new ActorCommand.MoveTo(mutable);
        mutable.set(10, 80, 10);

        assertEquals(new BlockPos(4, 70, -3), command.target());
        assertNotSame(mutable, command.target());
        assertEquals(ActorCommand.CommandType.MOVE_TO, command.type());
    }

    @Test
    void entityActionKeepsItsIntent() {
        ActorCommand.EntityAction command = new ActorCommand.EntityAction(
                42,
                ActorCommand.EntityActionType.INTERACT
        );

        assertEquals(42, command.entityId());
        assertEquals(ActorCommand.EntityActionType.INTERACT, command.action());
        assertEquals(ActorCommand.CommandType.ENTITY_ACTION, command.type());
    }

    @Test
    void stopCommandHasStopType() {
        assertEquals(ActorCommand.CommandType.STOP, new ActorCommand.Stop().type());
    }

    @Test
    void commandsRejectMissingRequiredValues() {
        assertThrows(NullPointerException.class, () -> new ActorCommand.MoveTo(null));
        assertThrows(NullPointerException.class, () -> new ActorCommand.EntityAction(1, null));
    }
}
