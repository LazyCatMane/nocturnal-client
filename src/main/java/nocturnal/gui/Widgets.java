package nocturnal.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.ListSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.setting.Setting;
import nocturnal.util.Keys;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One GUI row per setting. A new setting type only needs a case in forSetting(). */
final class Widgets {
    private static final int TRACK = 0xFF26262F, PAD = 2;

    /** The widget currently capturing keyboard input, if any. */
    static Widget focused;

    /** Mouse position in GUI units, set by the screen each frame for hover effects. */
    static int mouseX, mouseY;

    private Widgets() {}

    static Widget forSetting(Setting<?> setting) {
        return switch (setting) {
            case BoolSetting s -> new Bool(s);
            case NumberSetting s -> new Slider(s);
            case EnumSetting<?> s -> new Cycle(s);
            case ListSetting<?> s -> dropdown(s);
            default -> new Text(setting);
        };
    }

    private static <T> Widget dropdown(ListSetting<T> setting) {
        return new Dropdown<>(setting);
    }

    abstract static class Widget {
        abstract String description();

        /** Height this frame, given the panel's row height. Widgets that grow, like an open dropdown, return more. */
        int height(int row) { return row; }

        /** Draws into the row x..x+w, y..y+h. */
        abstract void render(GuiGraphics g, int x, int y, int w, int h);

        /** For widgets taller than a row that need to know where in them the click landed. */
        void click(double mouseX, double mouseY, int x, int y, int w, int button) {
            click(mouseX, x, w, button);
        }

        void click(double mouseX, int x, int w, int button) {}

        /** Returns true if the wheel was used, so the panel doesn't scroll too. */
        boolean scroll(int direction) { return false; }

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
        static String fitEnd(String s, int width) {
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

    /**
     * Every registry entry behind a header that opens it. Type in the box to filter. Each entry shows its item icon,
     * which turns into a ticked checkbox once selected, so icons cost no extra width.
     */
    static final class Dropdown<T> extends Widget {
        private static final int SEARCH = 13, OPTION = 12, VISIBLE = 8, FOOTER = 12, GAP = 2, ICON = 10, BOX = 9;
        private static final int OPEN_HEIGHT = SEARCH + GAP + VISIBLE * OPTION + FOOTER + GAP;

        private final ListSetting<T> setting;
        private final Anim expand = new Anim(14, 0);
        /** Matches for the query, checked ones first. Only rebuilt on open and while typing so rows don't jump when clicked. */
        private List<T> shown = List.of();
        private String query = "";
        private boolean open, typing;
        private int row, scroll, top;
        /** The option under the mouse this frame, named in the tooltip. */
        private T hovered;

        Dropdown(ListSetting<T> setting) { this.setting = setting; }

        /** The hovered option's full name, or the setting's description over the header. */
        String description() {
            if (hovered != null) return setting.name(hovered);
            return mouseY >= top + row ? null : setting.description + " (click to open, right click to reset)";
        }

        int height(int row) {
            this.row = row;
            return row + Math.round(Ui.ease(expand.update(open ? 1 : 0)) * OPEN_HEIGHT);
        }

        private void refresh() {
            String q = query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            List<T> on = new ArrayList<>(), off = new ArrayList<>();
            for (T entry : setting.options()) {
                if (!setting.id(entry).contains(q)) continue;
                (setting.contains(entry) ? on : off).add(entry);
            }
            on.addAll(off);
            shown = on;
            scroll = Math.clamp(scroll, 0, Math.max(0, shown.size() - VISIBLE));
        }

        void render(GuiGraphics g, int x, int y, int w, int h) {
            typing = focused == this;
            top = y;
            hovered = null;
            Ui.text(g, setting.name, x + PAD, textY(y, row), Ui.TEXT);
            String count = String.valueOf(setting.get().size());
            float px = x + w - PAD - Ui.width(count) - 13;
            Ui.rect(g, px, y + 2, x + w - PAD, y + row - 2, 3, open ? Ui.withAlpha(Ui.gradient(0), 0.35f) : Ui.SURFACE);
            Ui.text(g, count, px + 3, textY(y, row), Ui.TEXT);
            float ax = x + w - PAD - 7, ay = y + row / 2f - 1.5f;
            for (int i = 0; i < 3; i++) {
                float rowY = open ? ay + 2 - i : ay + i;
                Ui.rect(g, ax + i, rowY, ax + 5 - i, rowY + 1, 0, Ui.TEXT);
            }
            if (h <= row) return;

            g.enableScissor(x, y + row, x + w, y + h);
            int left = x + PAD, right = x + w - PAD;
            int sy = y + row;
            Ui.rect(g, left, sy, right, sy + SEARCH, 3, Ui.SURFACE);
            float sty = textY(sy, SEARCH);
            if (query.isEmpty() && !typing) {
                Ui.text(g, "Search...", left + 4, sty, Ui.MUTED);
            } else {
                String text = Text.fitEnd(query, right - left - 10);
                Ui.text(g, text, left + 4, sty, Ui.TEXT);
                if (typing && System.currentTimeMillis() / 500 % 2 == 0) {
                    float cx = left + 5 + Ui.width(text);
                    Ui.rect(g, cx, sy + 2, cx + 1, sy + SEARCH - 2, 0, Ui.gradient(0));
                }
            }
            if (typing) Ui.rect(g, left + 2, sy + SEARCH - 1, right - 2, sy + SEARCH, 0, Ui.gradient(0), Ui.gradient(0.25f));

            int ly = sy + SEARCH + GAP;
            boolean bar = shown.size() > VISIBLE;
            int textRight = right - (bar ? 4 : 0);
            if (shown.isEmpty()) Ui.text(g, "No matches", left + 2, textY(ly, OPTION), Ui.MUTED);
            for (int i = 0; i < VISIBLE && scroll + i < shown.size(); i++) {
                T entry = shown.get(scroll + i);
                int oy = ly + i * OPTION;
                boolean checked = setting.contains(entry);
                if (mouseX >= left && mouseX < textRight && mouseY >= oy && mouseY < oy + OPTION && mouseY < y + h) {
                    Ui.rect(g, left, oy, textRight, oy + OPTION, 3, 0x0CFFFFFF);
                    hovered = entry;
                }
                float ix = left + 1, iy = oy + (OPTION - ICON) / 2f;
                ItemStack icon = setting.icon(entry);
                if (checked || icon.isEmpty()) checkbox(g, ix + (ICON - BOX) / 2f, iy + (ICON - BOX) / 2f, checked);
                else icon(g, icon, ix, iy);
                String id = Ui.fit(setting.id(entry), (int) (textRight - ix - ICON - 4));
                Ui.text(g, id, ix + ICON + 3, textY(oy, OPTION), checked ? Ui.TEXT : Ui.MUTED);
            }
            if (bar) {
                float trackY0 = ly, trackY1 = ly + VISIBLE * OPTION;
                float thumb = Math.max(6, (trackY1 - trackY0) * VISIBLE / shown.size());
                float t = (float) scroll / (shown.size() - VISIBLE);
                float thumbY = trackY0 + (trackY1 - trackY0 - thumb) * t;
                Ui.rect(g, right - 2, trackY0, right, trackY1, 1, TRACK);
                Ui.rect(g, right - 2, thumbY, right, thumbY + thumb, 1, Ui.gradient(0));
            }

            int fy = ly + VISIBLE * OPTION;
            Ui.text(g, setting.get().size() + "/" + setting.options().size(), left + 2, textY(fy, FOOTER), Ui.MUTED);
            int noneX = noneX(right), allX = allX(right);
            Ui.text(g, "All", allX, textY(fy, FOOTER), over(allX, "All", fy) ? Ui.TEXT : Ui.MUTED);
            Ui.text(g, "None", noneX, textY(fy, FOOTER), over(noneX, "None", fy) ? Ui.TEXT : Ui.MUTED);
            g.disableScissor();
        }

        private static void checkbox(GuiGraphics g, float x, float y, boolean checked) {
            if (!checked) {
                Ui.rect(g, x, y, x + BOX, y + BOX, 2, TRACK);
                return;
            }
            Ui.rect(g, x, y, x + BOX, y + BOX, 2, Ui.gradient(0), Ui.gradient(0.25f));
            // A tick drawn as a staircase of pixels: down two, then up three.
            int[][] tick = {{2, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2}};
            for (int[] p : tick) Ui.rect(g, x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, 0, 0xFFFFFFFF);
        }

        /** Draws a 16px item icon shrunk to ICON. */
        private static void icon(GuiGraphics g, ItemStack stack, float x, float y) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(ICON / 16f, ICON / 16f);
            g.renderItem(stack, 0, 0);
            g.pose().popMatrix();
        }

        private static int noneX(int right) { return right - 2 - Ui.width("None"); }

        private static int allX(int right) { return noneX(right) - 6 - Ui.width("All"); }

        private static boolean over(int x, String label, int fy) {
            return mouseX >= x - 1 && mouseX < x + Ui.width(label) + 1 && mouseY >= fy && mouseY < fy + FOOTER;
        }

        void click(double mouseX, double mouseY, int x, int y, int w, int button) {
            if (mouseY < y + row) {
                if (button == 0) {
                    open = !open;
                    if (open) refresh();
                } else if (button == 1) {
                    setting.reset();
                    if (open) refresh();
                }
                return;
            }
            if (!open || button != 0) return;
            int left = x + PAD, right = x + w - PAD;
            int sy = y + row, ly = sy + SEARCH + GAP, fy = ly + VISIBLE * OPTION;
            if (mouseY < sy + SEARCH) {
                focused = this;
                return;
            }
            // The screen clears focus on every click; keep typing into the filter after ticking a box.
            if (typing) focused = this;
            if (mouseY >= ly && mouseY < fy) {
                int i = scroll + (int) ((mouseY - ly) / OPTION);
                if (mouseX >= left && mouseX < right && i < shown.size()) setting.toggle(shown.get(i));
            } else if (mouseY >= fy && mouseY < fy + FOOTER) {
                int allX = allX(right), noneX = noneX(right);
                if (mouseX >= allX - 1 && mouseX < allX + Ui.width("All") + 1) setShown(true);
                else if (mouseX >= noneX - 1 && mouseX < noneX + Ui.width("None") + 1) setShown(false);
            }
        }

        /** Checks or unchecks every entry matching the filter. */
        private void setShown(boolean on) {
            Set<T> next = new LinkedHashSet<>(setting.get());
            if (on) next.addAll(shown);
            else next.removeAll(shown);
            setting.set(Collections.unmodifiableSet(next));
        }

        boolean scroll(int direction) {
            if (!open) return false;
            scroll = Math.clamp(scroll - direction, 0, Math.max(0, shown.size() - VISIBLE));
            return true;
        }

        boolean key(int key) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> focused = null;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (query.isEmpty()) return true;
                    query = query.substring(0, query.length() - 1);
                    refresh();
                }
                default -> { return false; }
            }
            return true;
        }

        void type(String chars) {
            query += chars;
            scroll = 0;
            refresh();
        }
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
