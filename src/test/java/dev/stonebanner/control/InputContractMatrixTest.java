package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.stonebanner.control.ActionResolver.*;

/**
 * P0 input contract: the highest modal layer must consume every interaction,
 * no matter which item, target, profile or physical binding produced the click.
 */
class InputContractMatrixTest {
    @Test void allContextsHonorModalPriority() {
        for (int flags = 0; flags < 64; flags++) {
            boolean gui = (flags & 1) != 0, construction = (flags & 2) != 0;
            boolean designation = (flags & 4) != 0, orders = (flags & 8) != 0;
            boolean bow = (flags & 16) != 0, mouse = (flags & 32) != 0;
            InputContext expected = gui ? InputContext.GUI : construction ? InputContext.CONSTRUCTION
                    : designation ? InputContext.DESIGNATION : orders ? InputContext.ORDERS
                    : bow ? InputContext.BOW : mouse ? InputContext.MOUSE : InputContext.WASD;
            assertEquals(expected, InputContext.resolve(gui, construction, designation,
                    orders, bow, mouse), "Context flags: " + flags);
        }
    }

    @Test void guiNeverLeaksActionsThroughAnyRemappedButtonOrTarget() {
        for (Button button : Button.values()) for (Target target : Target.values())
            for (int flags = 0; flags < 8; flags++) {
                boolean queued = (flags & 1) != 0, using = (flags & 2) != 0;
                boolean block = (flags & 4) != 0;
                assertEquals(Action.CONSUME, resolve(InputContext.GUI, button, target,
                        queued, using, block));
            }
    }

    @Test void toolsAndOrdersOwnWorldClicksInEveryTargetAndItemCombination() {
        for (Target target : Target.values()) for (int flags = 0; flags < 8; flags++) {
            boolean queued = (flags & 1) != 0, using = (flags & 2) != 0;
            boolean block = (flags & 4) != 0;
            assertEquals(Action.CONFIRM, resolve(InputContext.CONSTRUCTION, Button.PRIMARY,
                    target, queued, using, block));
            assertEquals(Action.CANCEL_TOOL, resolve(InputContext.CONSTRUCTION, Button.SECONDARY,
                    target, queued, using, block));
            assertEquals(Action.DESIGNATE, resolve(InputContext.DESIGNATION, Button.PRIMARY,
                    target, queued, using, block));
            assertEquals(Action.CANCEL_TOOL, resolve(InputContext.DESIGNATION, Button.SECONDARY,
                    target, queued, using, block));
            assertEquals(Action.SELECT, resolve(InputContext.ORDERS, Button.PRIMARY,
                    target, queued, using, block));
            assertEquals(Action.ORDER, resolve(InputContext.ORDERS, Button.SECONDARY,
                    target, queued, using, block));
            assertEquals(Action.CONSUME, resolve(InputContext.BOW, Button.PRIMARY,
                    target, queued, using, block));
            assertEquals(Action.USE_ITEM, resolve(InputContext.BOW, Button.SECONDARY,
                    target, queued, using, block));
        }
    }

    @Test void friendlyAndInventoryTargetsCannotBecomeHostileSecondaryActions() {
        for (InputContext mode : new InputContext[]{InputContext.WASD, InputContext.MOUSE})
            for (Target target : new Target[]{Target.FRIENDLY, Target.NEUTRAL, Target.INTERACTIVE_BLOCK})
                for (boolean holding : new boolean[]{false, true})
                    assertEquals(Action.INTERACT, resolve(mode, Button.SECONDARY, target,
                            false, holding, true));
    }

    @Test void mousePrimaryNeverFiresHeroUseOrAttack() {
        for (Target target : Target.values()) for (boolean item : new boolean[]{false,true}) {
            assertEquals(Action.MOVE, resolve(InputContext.MOUSE, Button.PRIMARY,
                    target, false, item, item));
            assertEquals(Action.QUEUE_MOVE, resolve(InputContext.MOUSE, Button.PRIMARY,
                    target, true, item, item));
        }
    }
}
