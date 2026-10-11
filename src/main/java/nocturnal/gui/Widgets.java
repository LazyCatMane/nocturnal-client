package nocturnal.gui;

import net.minecraft.client.gui.GuiGraphics;
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
    private static final int TRACK = 0xFF26262F, PAD = 2;

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

        /** Draws into the row x..x+w, y..y+h. */
        abstract void render(GuiGraphics g, int x, int y, int w, int h);

        void click(double mouseX, int x, int w, int button) {}

        void drag(double mouseX, int x, int w) {}

        void release() {}

        /** Returns true if the key was used. Only called while focused. */
        boolean key(int key) { return false; }

        void type(String chars) {}
    }

    private static float textY(int y, int h) {
        return y + (h - Ui.FONT_HEIGHT) / 2f;
    }

    static final class Bool extends Widget {
        private final BoolSetting setting;
        private final Anim knob;

        Bool(BoolSetting setting) {
            this.setting = setting;
            this.knob = new Anim(14, setting.get() ? 1 : 0);
        }

        String description() { return setting.description; }

        void render(GuiGraphics g, int x, int y, int w, int h) {
            float on = knob.update(setting.get() ? 1 : 0);
            Ui.text(g, setting.name, x + PAD, textY(y, h), Ui.mix(Ui.MUTED, Ui.TEXT, on));
            float tw = 16, th = 8, tx = x + w - tw - PAD, ty = y + (h - th) / 2f;
            Ui.rect(g, tx, ty, tx + tw, ty + th, th / 2, TRACK);
            Ui.rect(g, tx, ty, tx + tw, ty + th, th / 2, Ui.withAlpha(Ui.gradient(0), on), Ui.withAlpha(Ui.gradient(0.25f), on));
            float k = th - 3, kx = tx + 1.5f + (tw - k - 3) * on;
            Ui.rect(g, kx, ty + 1.5f, kx + k, ty + 1.5f + k, k / 2, 0xFFFFFFFF);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 0) setting.toggle();
        }
    }

    static final class Slider extends Widget {
        private final NumberSetting setting;
        private final Anim fill;
        private boolean dragging;

        Slider(NumberSetting setting) {
            this.setting = setting;
            this.fill = new Anim(18, (float) ratio());
        }

        String description() { return setting.description + " (right click to reset)"; }

        private double ratio() {
            return (setting.get() - setting.min) / (setting.max - setting.min);
        }

        void render(GuiGraphics g, int x, int y, int w, int h) {
            float r = fill.update((float) ratio());
            String value = setting.display();
            Ui.text(g, setting.name, x + PAD, y + 1, Ui.TEXT);
            Ui.text(g, value, x + w - PAD - Ui.width(value), y + 1, Ui.MUTED);
            float x0 = x + PAD, x1 = x + w - PAD, ty = y + h - 3.5f;
            Ui.rect(g, x0, ty, x1, ty + 2, 1, TRACK);
            float end = x0 + (x1 - x0) * r;
            Ui.rect(g, x0, ty, Math.max(end, x0 + 2), ty + 2, 1, Ui.gradient(0), Ui.gradient(0.25f));
            Ui.rect(g, end - 2.5f, ty - 1.5f, end + 2.5f, ty + 3.5f, 2.5f, 0xFFFFFFFF);
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
            double ratio = Math.max(0, Math.min(1, (mouseX - x - PAD) / (w - PAD * 2)));
            setting.set(setting.min + ratio * (setting.max - setting.min));
        }

        void release() { dragging = false; }
    }

    static final class Cycle extends Widget {
        private final EnumSetting<?> setting;

        Cycle(EnumSetting<?> setting) { this.setting = setting; }

        String description() { return setting.description + " (left/right click to cycle)"; }

        void render(GuiGraphics g, int x, int y, int w, int h) {
            String value = pretty(setting.display());
            Ui.text(g, setting.name, x + PAD, textY(y, h), Ui.TEXT);
            int vw = Ui.width(value);
            float px = x + w - PAD - vw - 6;
            Ui.rect(g, px, y + 2, x + w - PAD, y + h - 2, 3, Ui.SURFACE);
            Ui.text(g, value, px + 3, textY(y, h), Ui.TEXT);
        }

        void click(double mouseX, int x, int w, int button) {
            if (button == 0 || button == 1) setting.cycle(button == 0 ? 1 : -1);
        }

        /** SAFE_WALK -> Safe walk. */
        private static String pretty(String name) {
            String s = name.replace('_', ' ').toLowerCase();
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    /** Free-text editing for string, color and any other setting type: click, type, Enter. */
    static final class Text extends Widget {
        private final Setting<?> setting;
        private String buffer;

        Text(Setting<?> setting) { this.setting = setting; }

        String description() { return setting.description + " (click to edit, Enter to save)"; }

        void render(GuiGraphics g, int x, int y, int w, int h) {
            boolean editing = focused == this;
            Ui.text(g, setting.name, x + PAD, textY(y, h), Ui.TEXT);
            int right = x + w - PAD;
            if (setting instanceof ColorSetting c && !editing) {
                Ui.rect(g, right - 14, y + 3, right, y + h - 3, 2.5f, 0x40FFFFFF);
                Ui.rect(g, right - 13.5f, y + 3.5f, right - 0.5f, y + h - 3.5f, 2, c.get());
                return;
            }
            int room = w - Ui.width(setting.name) - PAD * 3 - 4;
            boolean caret = editing && System.currentTimeMillis() / 500 % 2 == 0;
            String value = editing ? fitEnd(buffer, room - 3) : Ui.fit(setting.display(), room);
            float vx = right - Ui.width(value) - (editing ? 3 : 0);
            Ui.text(g, value, vx, textY(y, h), editing ? Ui.TEXT : Ui.MUTED);
            if (caret) Ui.rect(g, right - 2, y + 3, right - 1, y + h - 3, 0, Ui.gradient(0));
            if (editing) Ui.rect(g, x + PAD, y + h - 1, right, y + h, 0, Ui.gradient(0), Ui.gradient(0.25f));
        }

        /** Keeps the end of text being typed visible. */
        private static String fitEnd(String s, int width) {
            while (!s.isEmpty() && Ui.width(s) > width) s = s.substring(1);
            return s;
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

        void render(GuiGraphics g, int x, int y, int w, int h) {
            boolean listening = focused == this;
            Ui.text(g, "Keybind", x + PAD, textY(y, h), Ui.MUTED);
            String key = listening ? "..." : Keys.name(module.key());
            int kw = Ui.width(key);
            float px = x + w - PAD - kw - 6;
            Ui.rect(g, px, y + 2, x + w - PAD, y + h - 2, 3, listening ? Ui.gradient(0) : Ui.SURFACE);
            Ui.text(g, key, px + 3, textY(y, h), listening ? 0xFFFFFFFF : Ui.TEXT);
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
