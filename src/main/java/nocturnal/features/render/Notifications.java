package nocturnal.features.render;

import net.minecraft.client.gui.GuiGraphics;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.gui.Anim;
import nocturnal.gui.Ui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

/** A toast in the bottom right whenever a module is turned on or off. */
public class Notifications extends Module {
    private static final int ON = 0xFF5CE59A, OFF = 0xFFFF6B81;
    private static final int WIDTH = 124, HEIGHT = 30, GAP = 5, MARGIN = 4, RADIUS = 5, MAX = 5;

    private final NumberSetting duration = add(new NumberSetting("Duration", "Seconds each notification stays", 2, 0.5, 6, 0.5));

    private record Toast(Module module, boolean enabled, long shownAt, Anim slide) {}

    private final List<Toast> toasts = new ArrayList<>();

    public Notifications() {
        super("Notifications", "Pops up when a module is turned on or off", Category.RENDER);
        enabledByDefault();
    }

    @Override
    protected void onDisable() {
        toasts.clear();
    }

    @Subscribe
    private void onToggle(Events.ModuleToggle e) {
        if (!inGame() || e.module() == this) return;
        toasts.removeIf(t -> t.module() == e.module());
        toasts.add(new Toast(e.module(), e.enabled(), System.currentTimeMillis(), new Anim(12, 0)));
        while (toasts.size() > MAX) toasts.removeFirst();
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (toasts.isEmpty() || mc.options.hideGui) return;
        GuiGraphics g = e.graphics;
        long now = System.currentTimeMillis(), life = (long) (duration.get() * 1000);
        float right = g.guiWidth() - MARGIN;
        float y = g.guiHeight() - Math.max(MARGIN, Modules.get(Hud.class).bottomRightHeight()) - HEIGHT;

        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            float age = (now - t.shownAt()) / (float) life;
            float slide = Ui.ease(t.slide().update(age < 1 ? 1 : 0));
            if (age >= 1 && slide == 0) {
                toasts.remove(i);
                continue;
            }
            draw(g, t, right - (WIDTH + MARGIN) * slide + MARGIN, y, Math.min(age, 1), slide);
            y -= (HEIGHT + GAP) * slide;
        }
    }

    private void draw(GuiGraphics g, Toast t, float x0, float y0, float age, float fade) {
        float x1 = x0 + WIDTH, y1 = y0 + HEIGHT;
        int status = t.enabled() ? ON : OFF;
        Ui.alpha = fade;
        Ui.surface(g, x0, y0, x1, y1, RADIUS);
        float cy = y0 + HEIGHT / 2f;
        Ui.rect(g, x0 + 8, cy - 4, x0 + 16, cy + 4, 4, Ui.withAlpha(status, 0.18f));
        Ui.rect(g, x0 + 10, cy - 2, x0 + 14, cy + 2, 2, status);
        Ui.bold(g, Ui.fit(t.module().name, WIDTH - 30), x0 + 23, y0 + 5, Ui.TEXT);
        Ui.text(g, t.enabled() ? "Enabled" : "Disabled", x0 + 23, y0 + 15, Ui.MUTED);
        float barEnd = x0 + RADIUS + (WIDTH - RADIUS * 2) * (1 - age);
        Ui.rect(g, x0 + RADIUS, y1 - 1.5f, barEnd, y1 - 0.5f, 0.5f, Ui.withAlpha(Ui.gradient(0), 0.8f), Ui.withAlpha(Ui.gradient(0.3f), 0.8f));
        Ui.alpha = 1;
    }
}
