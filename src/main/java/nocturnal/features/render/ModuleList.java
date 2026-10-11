package nocturnal.features.render;

import net.minecraft.client.gui.GuiGraphics;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;

import java.util.Comparator;
import java.util.List;

public class ModuleList extends Module {
    private static final int INFO_COLOR = 0xFFAAAAAA;

    public ModuleList() {
        super("ModuleList", "Shows enabled modules in the top right", Category.RENDER);
        enabledByDefault();
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (mc.options.hideGui) return;
        GuiGraphics g = e.graphics;
        List<String[]> lines = Modules.all().stream()
            .filter(m -> m.isEnabled() && m != this)
            .map(m -> new String[]{m.name, m.info() == null ? "" : " " + m.info()})
            .sorted(Comparator.comparingInt((String[] l) -> mc.font.width(l[0] + l[1])).reversed())
            .toList();

        int y = 2;
        for (String[] line : lines) {
            int nameWidth = mc.font.width(line[0]);
            int x = g.guiWidth() - 2 - nameWidth - mc.font.width(line[1]);
            g.drawString(mc.font, line[0], x, y, ClickGui.accent(), true);
            g.drawString(mc.font, line[1], x + nameWidth, y, INFO_COLOR, true);
            y += mc.font.lineHeight + 1;
        }
    }
}
