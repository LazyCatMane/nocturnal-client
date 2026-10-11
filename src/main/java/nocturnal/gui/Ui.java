package nocturnal.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import nocturnal.Nocturnal;
import nocturnal.features.render.ClickGui;
import org.joml.Matrix3x2f;
/**
 * Drawing helpers for the client's own UI: the Inter font, anti-aliased rounded rectangles, soft shadows and the
 * animated accent gradient. Shapes are 9-slices of a small generated circle texture, so corners stay smooth at
 * any GUI scale. Everything is multiplied by {@link #alpha}, which screens set to fade their whole UI in.
 */
public final class Ui {
    public static final int TEXT = 0xFFF2F2F7, MUTED = 0xFF7D7D8C, BACKGROUND = 0xF20C0C11, SURFACE = 0xFF17171E, BORDER = 0x14FFFFFF;
    public static final int FONT_HEIGHT = 9;

    /** Fonts are rasterised for 1x to 4x pixel density in half steps (font/ui/*_10 .. *_40), so text is drawn 1:1. */
    private static final int MIN_DENSITY = 2, MAX_DENSITY = 8;
    private static final Style[] REGULAR = styles("regular"), BOLD = styles("bold");
    private static final int CIRCLE_SIZE = 32, GLOW_SIZE = 64;
    private static final float DESIGN_HEIGHT = 540;
    private static final Minecraft mc = Minecraft.getInstance();

    /** Opacity multiplier for everything drawn through this class. Reset it to 1 when done. */
    public static float alpha = 1;

    private static TextureSetup circle, glow;

    private Ui() {}

    // Colors

    /** The accent gradient at a position; offsets 0..1 apart sweep the full gradient, and it drifts over time. */
    public static int gradient(float offset) {
        double t = System.nanoTime() / 1e9 * ClickGui.gradientSpeed() + offset;
        float mix = (float) (Math.sin(t * Math.PI * 2) + 1) / 2;
        return ARGB.srgbLerp(mix, ClickGui.accent(), ClickGui.secondary());
    }

    public static int withAlpha(int argb, float a) {
        return ARGB.color(Mth.clamp(Math.round(ARGB.alpha(argb) * a), 0, 255), argb);
    }

    public static int mix(int from, int to, float t) {
        return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from, to);
    }

    // Scale

    /**
     * How much bigger to draw than Minecraft's GUI scale would. With fitWindow, a window DESIGN_HEIGHT pixels tall
     * gives 2 screen pixels per unit at scale 1. The result lands on a half step of screen pixels per unit, where
     * text is drawn 1:1 and stays sharp.
     */
    public static float sharpScale(float scale, boolean fitWindow) {
        float guiScale = mc.getWindow().getGuiScale();
        float pixels = scale * (fitWindow ? mc.getWindow().getHeight() / DESIGN_HEIGHT : guiScale);
        return Math.max(1, Math.round(pixels * 2) / 2f) / guiScale;
    }

    // Text

    private static Style[] styles(String weight) {
        Style[] styles = new Style[MAX_DENSITY + 1];
        for (int d = MIN_DENSITY; d <= MAX_DENSITY; d++) {
            Identifier id = Identifier.fromNamespaceAndPath(Nocturnal.ID, "ui/" + weight + "_" + d * 5);
            styles[d] = Style.EMPTY.withFont(new FontDescription.Resource(id));
        }
        return styles;
    }

    public static int width(String s) {
        return mc.font.width(Component.literal(s).withStyle(REGULAR[MAX_DENSITY]));
    }

    public static int boldWidth(String s) {
        return mc.font.width(Component.literal(s).withStyle(BOLD[MAX_DENSITY]));
    }

    public static void text(GuiGraphics g, String s, float x, float y, int color) {
        draw(g, s, false, x, y, color, null);
    }

    public static void bold(GuiGraphics g, String s, float x, float y, int color) {
        draw(g, s, true, x, y, color, null);
    }

    /** Text whose letters follow the accent gradient, starting at offset. */
    public static void gradientText(GuiGraphics g, String s, float x, float y, boolean bold, float offset) {
        draw(g, s, bold, x, y, 0xFFFFFFFF, offset);
    }

    /** Screen pixels per GUI unit where g is currently drawing. */
    private static float pixelScale(GuiGraphics g) {
        return mc.getWindow().getGuiScale() * g.pose().m00();
    }

    /** Cuts text to fit width, ending in an ellipsis. */
    public static String fit(String s, int width) {
        if (width(s) <= width) return s;
        while (!s.isEmpty() && width(s + "...") > width) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Picks the font made for this pixel density and snaps to whole pixels, so glyphs aren't resampled into blur. */
    private static void draw(GuiGraphics g, String s, boolean bold, float x, float y, int color, Float gradientOffset) {
        int argb = withAlpha(color, alpha);
        if (ARGB.alpha(argb) < 4) return;
        float scale = pixelScale(g);
        Style style = (bold ? BOLD : REGULAR)[Mth.clamp(Math.round(scale * 2), MIN_DENSITY, MAX_DENSITY)];
        Component c;
        if (gradientOffset != null) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < s.length(); i++) {
                line.append(Component.literal(String.valueOf(s.charAt(i))).withStyle(style.withColor(gradient(gradientOffset + i * 0.04f) & 0xFFFFFF)));
            }
            c = line;
        } else {
            c = Component.literal(s).withStyle(style);
        }
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x * scale) / scale, Math.round(y * scale) / scale);
        g.drawString(mc.font, c, 0, 0, argb, false);
        g.pose().popMatrix();
    }

    // Shapes

    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, float radius, int color) {
        rect(g, x0, y0, x1, y1, radius, color, color);
    }

    /** A rounded rectangle whose color runs from left to right. Radius 0 gives square corners. */
    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, float radius, int left, int right) {
        float r = Math.min(radius, Math.min(x1 - x0, y1 - y0) / 2);
        if (r < 0 || x1 <= x0 || y1 <= y0) return;
        int l = withAlpha(left, alpha), rt = withAlpha(right, alpha);
        submit(g, circle(), x0, y0, x1, y1, r, left == right ? x -> l : x -> mix(l, rt, (x - x0) / (x1 - x0)));
    }

    /** The standard box: soft shadow, hairline border and dark fill. */
    public static void surface(GuiGraphics g, float x0, float y0, float x1, float y1, float radius) {
        shadow(g, x0, y0, x1, y1, radius, 10, 0x66000000);
        float hair = 1 / pixelScale(g);
        rect(g, x0 - hair, y0 - hair, x1 + hair, y1 + hair, radius + hair, BORDER);
        rect(g, x0, y0, x1, y1, radius, BACKGROUND);
    }

    /** A soft shadow or glow that fades out over size around the rectangle. */
    public static void shadow(GuiGraphics g, float x0, float y0, float x1, float y1, float radius, float size, int color) {
        int c = withAlpha(color, alpha);
        if (ARGB.alpha(c) == 0) return;
        float r = radius + size;
        circle();
        submit(g, glow, x0 - size, y0 - size, x1 + size, y1 + size, r, x -> c);
    }

    /** Ease-out curve for 0..1 animation progress. */
    public static float ease(float t) {
        float f = 1 - Mth.clamp(t, 0, 1);
        return 1 - f * f * f;
    }

    /** Color of a vertex from its x position, for horizontal gradients. */
    private interface Shade { int at(float x); }

    private static void submit(GuiGraphics g, TextureSetup texture, float x0, float y0, float x1, float y1, float r, Shade colorAt) {
        ScreenRectangle scissor = g.scissorStack.peek();
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle area = new ScreenRectangle((int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1).transformMaxBounds(pose);
        ScreenRectangle bounds = scissor == null ? area : scissor.intersection(area);
        if (bounds == null) return;
        g.guiRenderState.submitGuiElement(new NineSlice(texture, pose, x0, y0, x1, y1, r, colorAt, scissor, bounds));
    }

    /** Nine quads cut from a circle texture: its quarters become the corners and its middle stretches across the rest. */
    private record NineSlice(TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1, float r,
                             Shade colorAt, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        public void buildVertices(VertexConsumer out) {
            float[] xs = {x0, x0 + r, x1 - r, x1}, ys = {y0, y0 + r, y1 - r, y1};
            float[] uv = {0, 0.5f, 0.5f, 1};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    vertex(out, xs[i], ys[j], uv[i], uv[j]);
                    vertex(out, xs[i], ys[j + 1], uv[i], uv[j + 1]);
                    vertex(out, xs[i + 1], ys[j + 1], uv[i + 1], uv[j + 1]);
                    vertex(out, xs[i + 1], ys[j], uv[i + 1], uv[j]);
                }
            }
        }

        private void vertex(VertexConsumer out, float x, float y, float u, float v) {
            out.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(colorAt.at(x));
        }
    }

    /** Creates both textures on first use, since the GPU isn't ready when classes load. Returns the circle. */
    private static TextureSetup circle() {
        if (circle == null) {
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            // The edge fades over 1.25 texels so corners stay smooth when drawn smaller than the texture.
            float half = CIRCLE_SIZE / 2f;
            circle = TextureSetup.singleTexture(bake("circle", CIRCLE_SIZE, d -> (1 - d) * half / 1.25f), linear);
            glow = TextureSetup.singleTexture(bake("glow", GLOW_SIZE, d -> (float) Math.exp(-4 * d * d) * (1 - d)), linear);
        }
        return circle;
    }

    private interface Falloff { float alpha(float distance); }

    /** A white square whose alpha depends on distance from the center, given as 0 in the middle and 1 at the edge. */
    private static GpuTextureView bake(String name, int size, Falloff falloff) {
        GpuTexture texture = RenderSystem.getDevice().createTexture(() -> "Nocturnal " + name,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, size, size, 1, 1);
        try (NativeImage image = new NativeImage(size, size, false)) {
            float half = size / 2f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float d = (float) Math.hypot(x + 0.5f - half, y + 0.5f - half) / half;
                    int a = Math.round(Mth.clamp(falloff.alpha(d), 0, 1) * 255);
                    image.setPixel(x, y, ARGB.color(a, 0xFFFFFF));
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
        }
        return RenderSystem.getDevice().createTextureView(texture);
    }
}
