package nocturnal.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import nocturnal.config.Config;
import nocturnal.features.render.ClickGui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/** One draggable column per category. Left click toggles a module, right click shows its settings. */
final class Panel {
    static final int WIDTH = 110;
    static final int ROW = 13;
    private static final int BACKGROUND = 0xE0141018;
    private static final int SETTINGS_BACKGROUND = 0xE00C0A0F;

    private static final class Entry {
        final Module module;
        final List<Widgets.Widget> widgets = new ArrayList<>();
        boolean expanded;

        Entry(Module module) {
            this.module = module;
            widgets.add(new Widgets.Bind(module));
            for (Setting<?> s : module.settings()) widgets.add(Widgets.forSetting(s));
        }
    }

    final Category category;
    private final Config.PanelState state;
    private final List<Entry> entries = new ArrayList<>();
    private boolean dragging;
    private double grabX, grabY;
    private Widgets.Widget active;
    private int activeX;

    Panel(Category category, int defaultX) {
        this.category = category;
        this.state = Config.panel(category.name(), defaultX, 10);
        for (Module m : Modules.in(category)) entries.add(new Entry(m));
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Draws the panel and returns the description of the hovered row, or null. */
    String render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = state.x, y = state.y;
        String hovered = null;

        g.fill(x, y, x + WIDTH, y + ROW, ClickGui.accent());
        g.drawString(font, category.title, x + (WIDTH - font.width(category.title)) / 2, y + 3, 0xFFFFFFFF, true);
        y += ROW;
        if (!state.open) return null;

        for (Entry e : entries) {
            g.fill(x, y, x + WIDTH, y + ROW, BACKGROUND);
            g.drawString(font, e.module.name, x + 4, y + 3, e.module.isEnabled() ? ClickGui.accent() : Widgets.TEXT, false);
            g.drawString(font, e.expanded ? "-" : "+", x + WIDTH - 8, y + 3, Widgets.MUTED, false);
            if (inside(mouseX, mouseY, x, y, WIDTH)) hovered = e.module.description;
            y += ROW;

            if (!e.expanded) continue;
            for (Widgets.Widget w : e.widgets) {
                g.fill(x, y, x + WIDTH, y + ROW, SETTINGS_BACKGROUND);
                g.fill(x, y, x + 1, y + ROW, ClickGui.accent());
                w.render(g, font, x + 4, y, WIDTH - 4, ROW);
                if (inside(mouseX, mouseY, x, y, WIDTH)) hovered = w.description();
                y += ROW;
            }
        }
        return hovered;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = state.x, y = state.y;
        if (inside(mouseX, mouseY, x, y, WIDTH)) {
            if (button == 0) {
                dragging = true;
                grabX = mouseX - x;
                grabY = mouseY - y;
            } else if (button == 1) {
                state.open = !state.open;
                Config.markDirty();
            }
            return true;
        }
        if (!state.open) return false;
        y += ROW;

        for (Entry e : entries) {
            if (inside(mouseX, mouseY, x, y, WIDTH)) {
                if (button == 0) e.module.toggle();
                else if (button == 1) e.expanded = !e.expanded;
                return true;
            }
            y += ROW;
            if (!e.expanded) continue;
            for (Widgets.Widget w : e.widgets) {
                if (inside(mouseX, mouseY, x, y, WIDTH)) {
                    active = w;
                    activeX = x + 4;
                    w.click(mouseX, activeX, WIDTH - 4, button);
                    return true;
                }
                y += ROW;
            }
        }
        return false;
    }

    void mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            state.x = (int) (mouseX - grabX);
            state.y = (int) (mouseY - grabY);
            Config.markDirty();
        } else if (active != null) {
            active.drag(mouseX, activeX, WIDTH - 4);
        }
    }

    void mouseReleased() {
        dragging = false;
        if (active != null) active.release();
        active = null;
    }

    void scroll(int amount) {
        state.y += amount;
        Config.markDirty();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW;
    }
}
