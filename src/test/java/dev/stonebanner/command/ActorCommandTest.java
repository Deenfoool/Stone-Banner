package dev.stonebanner.command;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorCommandTest {
    @Test
    void moveTargetIsCopiedAsImmutablePosition() {
        ActorCommand.MoveTo command = new ActorCommand.MoveTo(new BlockPos(4, 70, -8));
        assertEquals(new BlockPos(4, 70, -8), command.target());
    }

    @Test
    void followDistanceHasSafeMinimum() {
        ActorCommand.FollowEntity command = new ActorCommand.FollowEntity(12, 0.1D);
        assertEquals(1.0D, command.preferredDistance());
    }
}
