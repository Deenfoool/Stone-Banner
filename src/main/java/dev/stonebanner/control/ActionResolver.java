package dev.stonebanner.control;

/** Pure input contract shared by mouse and remapped keyboard action bindings. */
public final class ActionResolver {
    public enum Button { PRIMARY, SECONDARY }
    public enum Target { NONE, GROUND, BLOCK, INTERACTIVE_BLOCK, FRIENDLY, HOSTILE }
    public enum Action { CONSUME, CONFIRM, CANCEL_TOOL, DESIGNATE, SELECT, ORDER,
        MOVE, QUEUE_MOVE, ATTACK_OR_MINE, INTERACT, USE_ITEM }
    private ActionResolver() {}

    public static Action resolve(InputContext context, Button button, Target target,
                                 boolean queue, boolean usingItem, boolean placeBlock) {
        boolean primary = button == Button.PRIMARY;
        return switch (context) {
            case GUI -> Action.CONSUME;
            case CONSTRUCTION -> primary ? Action.CONFIRM : Action.CANCEL_TOOL;
            case DESIGNATION -> primary ? Action.DESIGNATE : Action.CANCEL_TOOL;
            case ORDERS -> primary ? Action.SELECT : Action.ORDER;
            case BOW -> primary ? Action.CONSUME : Action.USE_ITEM;
            case MOUSE -> {
                if (primary) yield queue ? Action.QUEUE_MOVE : Action.MOVE;
                if (target == Target.INTERACTIVE_BLOCK || target == Target.FRIENDLY) yield Action.INTERACT;
                if (usingItem || placeBlock) yield Action.USE_ITEM;
                yield target == Target.NONE ? Action.CONSUME : Action.ATTACK_OR_MINE;
            }
            case WASD -> {
                if (primary) yield target == Target.FRIENDLY || target == Target.NONE
                        ? Action.CONSUME : Action.ATTACK_OR_MINE;
                if (target == Target.INTERACTIVE_BLOCK || target == Target.FRIENDLY || target == Target.HOSTILE)
                    yield Action.INTERACT;
                yield Action.USE_ITEM;
            }
        };
    }
}
