package nocturnal.features.render;

import net.minecraft.client.gui.GuiGraphics;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.gui.Anim;
import nocturnal.gui.Ui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Enabled modules in the top right, longest first. Names slide in when turned on and out when turned off. */
public class ModuleList extends Module {
    private static final int LINE = 12, PADDING = 4, BAR = 2, MARGIN = 2;
    private static final float GRADIENT_STEP = 0.06f;

    private final BoolSetting background = add(new BoolSetting("Background", "Dark bar behind each name", true));
    private final BoolSetting sideLine = add(new BoolSetting("Side line", "Accent line along the right edge", true));
    private final BoolSetting info = add(new BoolSetting("Info", "Show extra details such as modes next to names", true));

    /** Every module currently shown or sliding out. */
    private final Map<Module, Anim> slides = new LinkedHashMap<>();

    public ModuleList() {
        super("ModuleList", "Shows enabled modules in the top right", Category.RENDER);
        enabledByDefault();
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (mc.options.hideGui) return;
        for (Module m : Modules.all()) {
            if (m.isEnabled() && m != this && !(m instanceof ClickGui)) slides.computeIfAbsent(m, k -> new Anim(12, 0));
        }
        List<Map.Entry<Module, Anim>> lines = slides.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<Module, Anim> l) -> width(l.getKey())).reversed())
            .toList();

        GuiGraphics g = e.graphics;
        float right = g.guiWidth() - MARGIN, y = MARGIN;
        int index = 0;
        for (Map.Entry<Module, Anim> line : lines) {
            Module m = line.getKey();
            float shown = line.getValue().update(m.isEnabled() ? 1 : 0);
            if (shown == 0 && !m.isEnabled()) {
                slides.remove(m);
                continue;
            }
            float slide = Ui.ease(shown);
            int w = width(m) + PADDING * 2 + (sideLine.get() ? BAR : 0);
            float x0 = right - w * slide, x1 = x0 + w;
            int color = Ui.gradient(index * GRADIENT_STEP);
            Ui.alpha = slide;
            if (background.get()) Ui.rect(g, x0, y, x1, y + LINE, 0, 0xC00C0C11);
            if (sideLine.get()) Ui.rect(g, x1 - BAR, y, x1, y + LINE, 0, color);
            float ty = y + (LINE - Ui.FONT_HEIGHT) / 2f;
            Ui.text(g, m.name, x0 + PADDING, ty, color);
            String extra = extra(m);
            if (!extra.isEmpty()) Ui.text(g, extra, x0 + PADDING + Ui.width(m.name), ty, Ui.MUTED);
            Ui.alpha = 1;
            y += LINE * slide;
            index++;
        }
    }

    private String extra(Module m) {
        return info.get() && m.info() != null ? " " + m.info() : "";
    }

    private int width(Module m) {
        return Ui.width(m.name + extra(m));
    }
}
