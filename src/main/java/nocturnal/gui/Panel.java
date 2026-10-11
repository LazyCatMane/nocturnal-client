package nocturnal.gui;

import net.minecraft.client.gui.GuiGraphics;
import nocturnal.config.Config;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One draggable column per category. Left click toggles a module, right click shows its settings. */
final class Panel {
    static final int WIDTH = 120;
    private static final int HEADER = 20, ROW = 16, RADIUS = 6, INSET = 4, PAD = 10;
    private static final float GRADIENT_STEP = 0.04f;

    private static final class Entry {
        final Module module;
        final List<Widgets.Widget> widgets = new ArrayList<>();
        final Anim hover = new Anim(16, 0), enabled, expand = new Anim(14, 0);
        boolean expanded;
        float settingsHeight;

        Entry(Module module) {
            this.module = module;
            this.enabled = new Anim(12, module.isEnabled() ? 1 : 0);
            widgets.add(new Widgets.Bind(module));
            for (Setting<?> s : module.settings()) widgets.add(Widgets.forSetting(s));
        }
    }

    /** Where something clickable was drawn last frame. widget is null for a module row. */
    private record Hit(Entry entry, Widgets.Widget widget, int x, float y, int w) {}

    final Category category;
    private final Config.PanelState state;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Anim open;
    private boolean dragging;
    private double grabX, grabY;
    private Widgets.Widget active;
    private int activeX, activeW;

    Panel(Category category, int defaultX) {
        this.category = category;
        this.state = Config.panel(category.name(), defaultX, 10);
        this.open = new Anim(12, state.open ? 1 : 0);
        for (Module m : Modules.in(category)) entries.add(new Entry(m));
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Draws the panel and returns the description of the hovered row, or null. Hidden when nothing matches query.
     * The header is kept on screen, so a smaller window or larger scale never loses a panel.
     */
    String render(GuiGraphics g, int mouseX, int mouseY, String query, int screenWidth, int screenHeight) {
        hits.clear();
        state.x = Math.clamp(state.x, 0, Math.max(0, screenWidth - WIDTH));
        state.y = Math.min(state.y, Math.max(0, screenHeight - HEADER));
        List<Entry> shown = query.isEmpty() ? entries : entries.stream()
            .filter(e -> e.module.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        if (shown.isEmpty()) return null;

        float openness = Ui.ease(open.update(state.open || !query.isEmpty() ? 1 : 0));
        float content = 0;
        for (Entry e : shown) {
            e.settingsHeight = Ui.ease(e.expand.update(e.expanded ? 1 : 0)) * (e.widgets.size() * ROW + 4);
            content += ROW + e.settingsHeight;
        }
        float body = content == 0 ? 0 : (content + INSET * 2) * openness;
        int x = state.x, y = state.y;
        float bottom = y + HEADER + body;

        Ui.surface(g, x, y, x + WIDTH, bottom, RADIUS);
        float titleY = y + (HEADER - Ui.FONT_HEIGHT) / 2f;
        Ui.bold(g, category.title, x + PAD, titleY, Ui.TEXT);
        long on = entries.stream().filter(e -> e.module.isEnabled()).count();
        String count = on + "/" + entries.size();
        Ui.text(g, count, x + WIDTH - PAD - Ui.width(count), titleY, Ui.MUTED);
        Ui.rect(g, x + PAD, y + HEADER - 1, x + WIDTH - PAD, y + HEADER, 0.5f,
            Ui.withAlpha(Ui.gradient(0), 0.9f), Ui.withAlpha(Ui.gradient(0.3f), 0.15f));
        if (body < 1) return null;

        String hovered = null;
        float ry = y + HEADER + INSET;
        int rx0 = x + INSET, rx1 = x + WIDTH - INSET;
        g.enableScissor(x, y + HEADER, x + WIDTH, (int) Math.ceil(bottom));
        for (int i = 0; i < shown.size(); i++) {
            Entry e = shown.get(i);
            if (ry > bottom) break;
            boolean over = mouseX >= rx0 && mouseX < rx1 && mouseY >= ry && mouseY < ry + ROW && mouseY < bottom;
            float h = e.hover.update(over ? 1 : 0), lit = e.enabled.update(e.module.isEnabled() ? 1 : 0);
            float offset = i * GRADIENT_STEP;

            Ui.rect(g, rx0, ry + 1, rx1, ry + ROW - 1, 4, Ui.withAlpha(0x0CFFFFFF, h));
            if (lit > 0) {
                int accent = Ui.gradient(offset);
                Ui.rect(g, rx0, ry + 1, rx1, ry + ROW - 1, 4, Ui.withAlpha(accent, 0.1f * lit));
                Ui.rect(g, rx0 + 2, ry + 5, rx0 + 4, ry + ROW - 5, 1, Ui.withAlpha(accent, lit));
            }
            float ty = ry + (ROW - Ui.FONT_HEIGHT) / 2f;
            Ui.text(g, e.module.name, rx0 + PAD - INSET + 2 * lit, ty, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(lit, h * 0.6f)));
            hits.add(new Hit(e, null, rx0, ry, rx1 - rx0));
            if (over) hovered = e.module.description;
            ry += ROW;

            if (e.settingsHeight < 0.5f) continue;
            float sy1 = ry + e.settingsHeight;
            Ui.rect(g, rx0, ry, rx1, sy1 - 2, 4, 0x40000000);
            g.enableScissor(rx0, (int) ry, rx1, (int) Math.ceil(sy1));
            float wy = ry + 2;
            for (Widgets.Widget w : e.widgets) {
                if (wy > sy1) break;
                w.render(g, rx0 + 4, Math.round(wy), rx1 - rx0 - 8, ROW);
                hits.add(new Hit(e, w, rx0 + 4, wy, rx1 - rx0 - 8));
                if (mouseX >= rx0 && mouseX < rx1 && mouseY >= wy && mouseY < Math.min(wy + ROW, sy1)) hovered = w.description();
                wy += ROW;
            }
            g.disableScissor();
            ry = sy1;
        }
        g.disableScissor();
        return hovered;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = state.x, y = state.y;
        if (mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEADER) {
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
        for (Hit hit : hits) {
            if (mouseX < hit.x() || mouseX >= hit.x() + hit.w() || mouseY < hit.y() || mouseY >= hit.y() + ROW) continue;
            if (hit.widget() == null) {
                if (button == 0) hit.entry().module.toggle();
                else if (button == 1) hit.entry().expanded = !hit.entry().expanded;
            } else {
                active = hit.widget();
                activeX = hit.x();
                activeW = hit.w();
                active.click(mouseX, activeX, activeW, button);
            }
            return true;
        }
        return false;
    }

    void mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            state.x = (int) (mouseX - grabX);
            state.y = (int) (mouseY - grabY);
            Config.markDirty();
        } else if (active != null) {
            active.drag(mouseX, activeX, activeW);
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
}
