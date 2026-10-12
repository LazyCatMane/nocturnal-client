package nocturnal.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import nocturnal.config.Config;
import nocturnal.features.render.ClickGui;
import nocturnal.features.render.GuiBlur;
import nocturnal.module.Category;
import nocturnal.module.Modules;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Type anywhere to search modules; Escape clears the search, then closes. */
public class ClickGuiScreen extends Screen {
    private static final int SEARCH_WIDTH = 160, SEARCH_HEIGHT = 18, SEARCH_MAX = 24, SLIDE = 8;

    private final List<Panel> panels = new ArrayList<>();
    private final Anim opening = new Anim(10, 0);
    private final Anim searchGlow = new Anim(12, 0);
    private String query = "";
    /** Fixed while a mouse button is held, so dragging the Scale slider doesn't move the slider under the mouse. */
    private float scale = 1;
    private boolean mouseDown;
    /** The key press that opens this screen is also delivered to it; ignore the close key until the first frame. */
    private boolean shown;

    public ClickGuiScreen() {
        super(Component.literal("Nocturnal"));
        int x = 10;
        for (Category c : Category.values()) {
            Panel p = new Panel(c, x);
            if (p.isEmpty()) continue;
            panels.add(p);
            x += Panel.WIDTH + 8;
        }
        Widgets.focused = null;
        scale = Modules.get(ClickGui.class).menuScale();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        shown = true;
        if (!mouseDown) scale = Modules.get(ClickGui.class).menuScale();
        int mx = (int) (mouseX / scale), my = (int) (mouseY / scale);
        Widgets.mouseX = mx;
        Widgets.mouseY = my;
        float in = Ui.ease(opening.update(1));
        Ui.alpha = in;
        g.pose().pushMatrix();
        g.pose().scale(scale);
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - in) * SLIDE);
        String hovered = null;
        for (Panel p : panels) {
            String h = p.render(g, mx, my, query, scaledWidth(), scaledHeight());
            if (h != null) hovered = h;
        }
        g.pose().popMatrix();
        renderSearch(g);
        if (hovered != null) renderTooltip(g, hovered, mx, my);
        g.pose().popMatrix();
        Ui.alpha = 1;
    }

    private int scaledWidth() {
        return (int) (width / scale);
    }

    private int scaledHeight() {
        return (int) (height / scale);
    }

    private void renderSearch(GuiGraphics g) {
        float x0 = (scaledWidth() - SEARCH_WIDTH) / 2f, y0 = scaledHeight() - SEARCH_HEIGHT - 10, x1 = x0 + SEARCH_WIDTH, y1 = y0 + SEARCH_HEIGHT;
        float glow = searchGlow.update(query.isEmpty() ? 0 : 1);
        Ui.shadow(g, x0, y0, x1, y1, SEARCH_HEIGHT / 2f, 8, Ui.withAlpha(Ui.gradient(0), 0.3f * glow));
        Ui.surface(g, x0, y0, x1, y1, SEARCH_HEIGHT / 2f);
        float ty = y0 + (SEARCH_HEIGHT - Ui.FONT_HEIGHT) / 2f;
        if (query.isEmpty()) {
            Ui.text(g, "Search modules...", x0 + 10, ty, Ui.MUTED);
            return;
        }
        Ui.text(g, query, x0 + 10, ty, Ui.TEXT);
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            float cx = x0 + 11 + Ui.width(query);
            Ui.rect(g, cx, y0 + 5, cx + 1, y1 - 5, 0, Ui.gradient(0));
        }
    }

    private void renderTooltip(GuiGraphics g, String text, int mouseX, int mouseY) {
        int w = Ui.width(text) + 12, h = Ui.FONT_HEIGHT + 8;
        float x = Math.min(mouseX + 10, scaledWidth() - w - 4), y = Math.min(mouseY + 10, scaledHeight() - h - 4);
        Ui.surface(g, x, y, x + w, y + h, 4);
        Ui.text(g, text, x + 6, y + 4, Ui.TEXT);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiBlur background = Modules.get(GuiBlur.class);
        if (!background.isEnabled()) return;
        if (background.blur.get()) renderBlurredBackground(g);
        int dim = (int) (background.dim.get() * 255 * Ui.ease(opening.get()));
        if (dim > 0) g.fill(0, 0, width, height, ARGB.color(dim, 0x000000));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Widgets.focused = null;
        mouseDown = true;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.mouseClicked(event.x() / scale, event.y() / scale, event.button())) {
                panels.remove(i);
                panels.add(p);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        for (Panel p : panels) p.mouseDragged(event.x() / scale, event.y() / scale);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        mouseDown = false;
        for (Panel p : panels) p.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).scrollWidget(mouseX / scale, mouseY / scale, (int) Math.signum(vertical))) return true;
        }
        for (Panel p : panels) p.scroll((int) (vertical * 15));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Widgets.focused != null) {
            Widgets.focused.key(event.key());
            return true;
        }
        if (!query.isEmpty()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                query = "";
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                query = event.hasControlDown() ? "" : query.substring(0, query.length() - 1);
                return true;
            }
        }
        if (shown && query.isEmpty() && event.key() == Modules.get(ClickGui.class).key()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Widgets.focused != null) {
            Widgets.focused.type(event.codepointAsString());
        } else if (query.length() < SEARCH_MAX && !Character.isISOControl(event.codepoint())) {
            query += event.codepointAsString();
        }
        return true;
    }

    @Override
    public void removed() {
        Widgets.focused = null;
        Ui.alpha = 1;
        Config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
