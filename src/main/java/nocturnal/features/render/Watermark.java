package nocturnal.features.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.gui.Ui;
import nocturnal.module.Category;
import nocturnal.module.Module;

/**
 * Client name in the top left. To add a logo, put a square PNG at
 * src/main/resources/assets/nocturnal/textures/gui/logo.png and rebuild; it's drawn left of the name.
 */
public class Watermark extends Module {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Nocturnal.ID, "textures/gui/logo.png");
    private static final String NAME = "Nocturnal";
    private static final int MARGIN = 4, HEIGHT = 20, PADDING = 8, LOGO_SIZE = 12, RADIUS = 5;

    private Boolean hasLogo;

    public Watermark() {
        super("Watermark", "Client name and logo in the top left", Category.RENDER);
        enabledByDefault();
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (hasLogo == null) hasLogo = mc.getResourceManager().getResource(LOGO).isPresent();
        GuiGraphics g = e.graphics;

        int logo = hasLogo ? LOGO_SIZE + 5 : 0;
        float x0 = MARGIN, y0 = MARGIN, x1 = x0 + PADDING * 2 + logo + Ui.boldWidth(NAME), y1 = y0 + HEIGHT;
        Ui.surface(g, x0, y0, x1, y1, RADIUS);
        if (hasLogo) {
            int ly = (int) (y0 + (HEIGHT - LOGO_SIZE) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, (int) x0 + PADDING, ly, 0, 0, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
        }
        Ui.gradientText(g, NAME, x0 + PADDING + logo, y0 + (HEIGHT - Ui.FONT_HEIGHT) / 2f, true, 0);
    }
}
