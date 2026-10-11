package nocturnal.util;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private Keys() {}

    public static String name(int key) {
        if (key < 0) return "None";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    /** Accepts display names like "Right Shift", "rightshift" or "rshift". Returns -1 for none/unknown. */
    public static int parse(String text) {
        String wanted = normalize(text);
        if (wanted.equals("none")) return -1;
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            String n = normalize(name(key));
            if (n.equals(wanted) || n.replace("right", "r").replace("left", "l").equals(wanted)) return key;
        }
        return -1;
    }

    private static String normalize(String s) {
        return s.replace(" ", "").toLowerCase();
    }
}
