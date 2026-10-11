package nocturnal.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import nocturnal.features.render.ClickGui;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.setting.Setting;
import nocturnal.util.Keys;
import org.lwjgl.glfw.GLFW;

/** One GUI row per setting. A new setting type only needs a case in forSetting(). */
final class Widgets {
    static final int TEXT = 0xFFDDDDDD;
    static final int MUTED = 0xFF8A8A8A;

    /** The widget currently capturing keyboard input, if any. */
    static Widget focused;

    private Widgets() {}

    static Widget forSetting(Setting<?> setting) {
        return switch (setting) {
            case BoolSetting s -> new Bool(s);
            case NumberSetting s -> new Slider(s);
            case EnumSetting<?> s -> new Cycle(s);
            default -> new Text(setting);
        };
    }

    abstract static class Widget {
        abstract String description();

        abstract void render(GuiGraphics g, Font font, int x, int y, int w, int h);

        void click(double mouseX, int x, int w, int button) {}

        void drag(double mouseX, int x, int w) {}

        void release() {}

        /** Returns true if the key was used. Only called while focused. */
        boolean key(int key) { return false; }

        void type(String chars) {}
    }

    static final class Bool extends Widget {
        private final BoolSetting setting;

        Bool(BoolSetting setting) { this.setting = setting; }

        String description() { return setting.description; }

        void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
            g.drawString(font, setting.name, x + 2, y + 2, TEXT, false);
            int box = h - 5;
            int bx = x + w - box - 3;
            g.fill(bx, y + 2, bx + box, y + 2 + box, setting.get() ? ClickGui.accent() : 0xFF3A3A3A);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 0) setting.toggle();
        }
    }

    static final class Slider extends Widget {
        private final NumberSetting setting;
        private boolean dragging;

        Slider(NumberSetting setting) { this.setting = setting; }

        String description() { return setting.description + " (right click to reset)"; }

        void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
            double ratio = (setting.get() - setting.min) / (setting.max - setting.min);
            g.fill(x, y, x + (int) (w * ratio), y + h, (ClickGui.accent() & 0x00FFFFFF) | 0x66000000);
            g.drawString(font, setting.name + ": " + setting.display(), x + 2, y + 2, TEXT, false);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 1) {
                setting.reset();
            } else if (button == 0) {
                dragging = true;
                drag(mouseX, x, w);
            }
        }

        void drag(double mouseX, int x, int w) {
            if (!dragging) return;
            double ratio = Math.max(0, Math.min(1, (mouseX - x) / w));
            setting.set(setting.min + ratio * (setting.max - setting.min));
        }

        void release() { dragging = false; }
    }

    static final class Cycle extends Widget {
        private final EnumSetting<?> setting;

        Cycle(EnumSetting<?> setting) { this.setting = setting; }

        String description() { return setting.description; }

        void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
            g.drawString(font, setting.name + ": " + setting.display(), x + 2, y + 2, TEXT, false);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 0 || button == 1) setting.cycle(button == 0 ? 1 : -1);
        }
    }

    /** Free-text editing for string, color and any other setting type: click, type, Enter. */
    static final class Text extends Widget {
        private final Setting<?> setting;
        private String buffer;

        Text(Setting<?> setting) { this.setting = setting; }

        String description() { return setting.description; }

        void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
            boolean editing = focused == this;
            String value = editing ? buffer + "_" : setting.display();
            g.drawString(font, setting.name + ": " + value, x + 2, y + 2, editing ? ClickGui.accent() : TEXT, false);
            if (setting instanceof ColorSetting c) g.fill(x + w - h + 2, y + 2, x + w - 3, y + h - 3, c.get());
        }

        void click(double mouseX, int x, int w, int button) {
            if (button != 0) return;
            buffer = setting.display();
            focused = this;
        }

        boolean key(int key) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    setting.parse(buffer);
                    focused = null;
                }
                case GLFW.GLFW_KEY_ESCAPE -> focused = null;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
                }
                default -> { return false; }
            }
            return true;
        }

        void type(String chars) { buffer += chars; }
    }

    static final class Bind extends Widget {
        private final Module module;

        Bind(Module module) { this.module = module; }

        String description() { return "Click, then press a key. Escape or Delete clears it."; }

        void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
            String text = focused == this ? "Bind: press a key..." : "Bind: " + Keys.name(module.key());
            g.drawString(font, text, x + 2, y + 2, focused == this ? ClickGui.accent() : MUTED, false);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 0) focused = this;
        }

        boolean key(int key) {
            boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE;
            module.setKey(clear ? -1 : key);
            focused = null;
            return true;
        }
    }
}
