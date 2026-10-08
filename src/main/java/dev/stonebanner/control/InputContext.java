package dev.stonebanner.control;

/** Highest active layer owns an input; GUI and tools never leak into hero actions. */
public enum InputContext {
    GUI, CONSTRUCTION, DESIGNATION, ORDERS, BOW, WASD, MOUSE;

    public static InputContext resolve(boolean gui, boolean construction, boolean designation,
                                       boolean orders, boolean bow, boolean mouse) {
        if (gui) return GUI;
        if (construction) return CONSTRUCTION;
        if (designation) return DESIGNATION;
        if (orders) return ORDERS;
        if (bow) return BOW;
        return mouse ? MOUSE : WASD;
    }
}
