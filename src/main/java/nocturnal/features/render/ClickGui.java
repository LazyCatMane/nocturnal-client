package nocturnal.features.render;

import nocturnal.gui.ClickGuiScreen;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.ColorSetting;
import org.lwjgl.glfw.GLFW;

public class ClickGui extends Module {
    private static final int DEFAULT_ACCENT = 0xFF9B5CFF;
    private static ClickGui instance;

    public final ColorSetting accent = add(new ColorSetting("Accent", "Color used by the GUI, HUD and chat", DEFAULT_ACCENT));

    public ClickGui() {
        super("ClickGui", "Opens this menu", Category.RENDER);
        defaultKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        instance = this;
    }

    public static int accent() {
        return instance == null ? DEFAULT_ACCENT : instance.accent.get();
    }

    @Override
    public void toggle() {
        mc.setScreen(new ClickGuiScreen());
    }
}
