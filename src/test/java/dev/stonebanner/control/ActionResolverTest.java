package dev.stonebanner.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.stonebanner.control.ActionResolver.*;

class ActionResolverTest {
    @Test void guiConsumesEveryCombination() {
        for(var button:Button.values())for(var target:Target.values())
            assertEquals(Action.CONSUME,resolve(InputContext.GUI,button,target,true,true,true));
    }
    @Test void modalLayersWinBeforeHeroOrBow() {
        assertEquals(InputContext.GUI,InputContext.resolve(true,true,true,true,true,true));
        assertEquals(InputContext.CONSTRUCTION,InputContext.resolve(false,true,true,true,true,true));
        assertEquals(InputContext.DESIGNATION,InputContext.resolve(false,false,true,true,true,true));
        assertEquals(InputContext.ORDERS,InputContext.resolve(false,false,false,true,true,true));
        assertEquals(Action.CANCEL_TOOL,resolve(InputContext.DESIGNATION,Button.SECONDARY,Target.HOSTILE,false,true,true));
    }
    @Test void mousePrimaryNeverInteractsOrAttacks() {
        for(var target:Target.values()) {
            assertEquals(Action.MOVE,resolve(InputContext.MOUSE,Button.PRIMARY,target,false,false,false));
            assertEquals(Action.QUEUE_MOVE,resolve(InputContext.MOUSE,Button.PRIMARY,target,true,false,false));
        }
    }
    @Test void friendlyTargetsAndContainersPreferInteraction() {
        for(var context:new InputContext[]{InputContext.WASD,InputContext.MOUSE})
            for(var target:new Target[]{Target.FRIENDLY,Target.INTERACTIVE_BLOCK})
                assertEquals(Action.INTERACT,resolve(context,Button.SECONDARY,target,false,false,false));
        assertEquals(Action.CONSUME,resolve(InputContext.WASD,Button.PRIMARY,Target.FRIENDLY,false,false,false));
    }
    @Test void wasdAttackIgnoresHeldBlockOrBowAndMouseUsesThem() {
        assertEquals(Action.ATTACK_OR_MINE,resolve(InputContext.WASD,Button.PRIMARY,Target.BLOCK,false,true,true));
        assertEquals(Action.USE_ITEM,resolve(InputContext.MOUSE,Button.SECONDARY,Target.BLOCK,false,true,false));
        assertEquals(Action.USE_ITEM,resolve(InputContext.MOUSE,Button.SECONDARY,Target.BLOCK,false,false,true));
        assertEquals(Action.ATTACK_OR_MINE,resolve(InputContext.MOUSE,Button.SECONDARY,Target.BLOCK,false,false,false));
    }
    @Test void ordersNeverBecomeHeroActions() {
        for(var target:Target.values()) {
            assertEquals(Action.SELECT,resolve(InputContext.ORDERS,Button.PRIMARY,target,true,true,true));
            assertEquals(Action.ORDER,resolve(InputContext.ORDERS,Button.SECONDARY,target,true,true,true));
        }
    }
}
