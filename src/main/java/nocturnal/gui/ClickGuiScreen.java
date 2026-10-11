package nocturnal.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import nocturnal.config.Config;
import nocturnal.features.render.ClickGui;
import nocturnal.module.Category;
import nocturnal.module.Modules;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private final List<Panel> panels = new ArrayList<>();
    /** The key press that opens this screen is also delivered to it; ignore the close key until the first frame. */
    private boolean shown;

    public ClickGuiScreen() {
        super(Component.literal("Nocturnal"));
        int x = 10;
        for (Category c : Category.values()) {
            Panel p = new Panel(c, x);
            if (p.isEmpty()) continue;
            panels.add(p);
            x += Panel.WIDTH + 6;
        }
        Widgets.focused = null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        shown = true;
        String hovered = null;
        for (Panel p : panels) {
            String h = p.render(g, font, mouseX, mouseY);
            if (h != null) hovered = h;
        }
        if (hovered != null) g.drawString(font, hovered, 4, height - 12, Widgets.TEXT, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Widgets.focused = null;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.mouseClicked(event.x(), event.y(), event.button())) {
                panels.remove(i);
                panels.add(p);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        for (Panel p : panels) p.mouseDragged(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (Panel p : panels) p.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (Panel p : panels) p.scroll((int) (vertical * 15));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Widgets.focused != null) {
            Widgets.focused.key(event.key());
            return true;
        }
        if (shown && event.key() == Modules.get(ClickGui.class).key()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Widgets.focused == null) return false;
        Widgets.focused.type(event.codepointAsString());
        return true;
    }

    @Override
    public void removed() {
        Widgets.focused = null;
        Config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
