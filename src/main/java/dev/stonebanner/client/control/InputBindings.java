package dev.stonebanner.client.control;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Poll the actual saved binding, including mouse buttons and scancodes, inside our input screen. */
public final class InputBindings {
    private InputBindings() {}
    public static boolean matches(KeyMapping mapping,int code,int scan) {
        return mapping.getKeyModifier().isActive(null) && (code>=10000
                ? mapping.matchesMouse(code-10000) : mapping.matches(code,scan));
    }
    public static boolean held(KeyMapping mapping) {
        var mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 1) return false;
        var key = mapping.getKey();
        if (key.equals(InputConstants.UNKNOWN)) return false;
        if (!mapping.getKeyModifier().isActive(null)) return false;
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.SCANCODE) {
            for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++)
                if (GLFW.glfwGetKeyScancode(code) == key.getValue())
                    return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            return false;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
