package dev.stonebanner.control;

/**
 * Pure input lifecycle for Hero/Orders switching.
 * A press changes mode immediately to keep the UI responsive. A short release keeps the new
 * mode; holding from Hero temporarily opens Orders and restores Hero on release.
 */
public final class HeroOrdersGesture {
    public enum Change { NONE, ENTER_ORDERS, RETURN_HERO }
    private boolean pressed;
    private boolean enteredFromHero;
    private long pressedAt;

    public boolean pressed() { return pressed; }

    public Change press(long nowMillis, boolean currentlyOrders) {
        if (pressed) return Change.NONE; // Ignore GLFW autorepeat.
        pressed = true;
        enteredFromHero = !currentlyOrders;
        pressedAt = nowMillis;
        return currentlyOrders ? Change.RETURN_HERO : Change.ENTER_ORDERS;
    }

    public Change release(long nowMillis, int holdMillis, boolean currentlyOrders) {
        if (!pressed) return Change.NONE;
        pressed = false;
        boolean temporary = enteredFromHero && currentlyOrders
                && nowMillis >= pressedAt && nowMillis - pressedAt >= holdMillis;
        enteredFromHero = false;
        return temporary ? Change.RETURN_HERO : Change.NONE;
    }

    /** Switching to another modal GUI, losing focus or changing worlds cannot leave held Orders sticky. */
    public Change interrupt(boolean currentlyOrders) {
        if (!pressed) return Change.NONE;
        boolean shouldReturn = enteredFromHero && currentlyOrders;
        reset();
        return shouldReturn ? Change.RETURN_HERO : Change.NONE;
    }

    public void reset() {
        pressed = false;
        enteredFromHero = false;
        pressedAt = 0;
    }
}
