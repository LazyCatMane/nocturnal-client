package nocturnal.features.render;

import nocturnal.gui.ClickGuiScreen;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public class ClickGui extends Module {
    private static final int DEFAULT_ACCENT = 0xFF9B5CFF, DEFAULT_SECONDARY = 0xFF2F8CFF;
    private static final double DEFAULT_SPEED = 0.25;
    /** Window height at which Fit window gives 2 screen pixels per unit, the menu's designed size. */
    private static final float DESIGN_HEIGHT = 540;
    private static ClickGui instance;

    public final ColorSetting accent = add(new ColorSetting("Accent", "Main color of the GUI, HUD and chat; the gradient starts here", DEFAULT_ACCENT));
    public final ColorSetting secondary = add(new ColorSetting("Secondary", "Where the accent gradient ends", DEFAULT_SECONDARY));
    public final NumberSetting gradientSpeed = add(new NumberSetting("Gradient speed", "How fast the gradient drifts; 0 holds it still", DEFAULT_SPEED, 0, 2, 0.05));
    public final NumberSetting scale = add(new NumberSetting("Scale", "Size of this menu", 1, 0.5, 2, 0.05));
    public final BoolSetting fitWindow = add(new BoolSetting("Fit window", "Keep this menu the same size relative to the window, whatever the window size or GUI scale", true));

    public ClickGui() {
        super("ClickGui", "Opens this menu", Category.RENDER);
        defaultKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        instance = this;
    }

    public static int accent() {
        return instance == null ? DEFAULT_ACCENT : instance.accent.get();
    }

    public static int secondary() {
        return instance == null ? DEFAULT_SECONDARY : instance.secondary.get();
    }

    public static double gradientSpeed() {
        return instance == null ? DEFAULT_SPEED : instance.gradientSpeed.get();
    }

    /**
     * How much bigger to draw the menu than Minecraft's GUI scale would. The result is rounded so the menu lands on
     * a half step of screen pixels per unit, where its text is drawn 1:1 and stays sharp.
     */
    public float menuScale() {
        float guiScale = mc.getWindow().getGuiScale();
        float pixels = scale.get().floatValue() * (fitWindow.get() ? mc.getWindow().getHeight() / DESIGN_HEIGHT : guiScale);
        return Math.max(1, Math.round(pixels * 2) / 2f) / guiScale;
    }

    @Override
    public void toggle() {
        mc.setScreen(new ClickGuiScreen());
    }
}
