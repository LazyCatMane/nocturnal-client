package nocturnal.features.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.gui.Ui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

/** Coordinates bottom left, armor beside the hotbar, and TPS, ping, speed and potion timers bottom right. */
public class Hud extends Module {
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final int HOTBAR_HALF_WIDTH = 91, ITEM = 16, MARGIN = 4;
    private static final int PADDING = 7, LINE = 12, LABEL_GAP = 12, BOX_HEIGHT = 18, RADIUS = 5, DOT = 3;
    private static final int TPS_SAMPLES = 5;
    private static final double NETHER_SCALE = 8;

    private final BoolSetting coords = add(new BoolSetting("Coords", "Your position, plus the matching Overworld/Nether position", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", "Your armor and its durability, right of the hotbar", true));
    private final BoolSetting tps = add(new BoolSetting("TPS", "Server ticks per second", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Your latency to the server", true));
    private final BoolSetting speed = add(new BoolSetting("Speed", "Horizontal speed in blocks per second", true));
    private final BoolSetting potions = add(new BoolSetting("Potions", "Active effects and time left", true));
    private final NumberSetting scale = add(new NumberSetting("Scale", "Size of the HUD: watermark, module list, these boxes and notifications, even while this module is off", 0.75, 0.5, 2, 0.05));
    private final BoolSetting fitWindow = add(new BoolSetting("Fit window", "Keep the HUD the same size relative to the window, whatever the window size or GUI scale", true));

    private final double[] tpsSamples = new double[TPS_SAMPLES];
    private int tpsCount, tpsIndex;
    private long lastTimePacket;
    private double blocksPerSecond;
    private int bottomRightHeight;

    public Hud() {
        super("Hud", "Coords, armor, TPS, ping, speed and potions", Category.RENDER);
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (!(e.packet instanceof ClientboundSetTimePacket)) return;
        long now = System.currentTimeMillis(), interval = now - lastTimePacket;
        lastTimePacket = now;
        // The server sends the time every 20 ticks, so 1000 ms between packets means 20 TPS.
        if (interval <= 0 || interval > 10_000) return;
        tpsSamples[tpsIndex] = Math.min(20, 20_000.0 / interval);
        tpsIndex = (tpsIndex + 1) % TPS_SAMPLES;
        tpsCount = Math.min(TPS_SAMPLES, tpsCount + 1);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (inGame()) blocksPerSecond = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo) * 20;
    }

    /** Scales g for the HUD; undo with g.pose().popMatrix(). Returns the scale, to divide the screen size by. */
    public static float pushScale(GuiGraphics g) {
        Hud hud = Modules.get(Hud.class);
        float s = Ui.sharpScale(hud.scale.get().floatValue(), hud.fitWindow.get());
        g.pose().pushMatrix();
        g.pose().scale(s);
        return s;
    }

    /** Height of the bottom-right box plus its margin, in HUD-scaled units, so notifications can sit above it. */
    public int bottomRightHeight() {
        return isEnabled() ? bottomRightHeight : 0;
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        bottomRightHeight = 0;
        if (!inGame() || mc.options.hideGui) return;
        GuiGraphics g = e.graphics;
        if (armor.get()) drawArmor(g, g.guiWidth() / 2 + HOTBAR_HALF_WIDTH + 4, g.guiHeight() - ITEM - 3);

        float s = pushScale(g);
        drawBoxes(g, g.guiWidth() / s, g.guiHeight() / s);
        g.pose().popMatrix();
    }

    private void drawBoxes(GuiGraphics g, float width, float height) {
        if (coords.get()) drawCoords(g, height - MARGIN - BOX_HEIGHT);

        List<String[]> right = new ArrayList<>();
        if (potions.get()) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                String name = effect.getEffect().value().getDisplayName().getString();
                if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);
                right.add(new String[]{name, effect.isInfiniteDuration() ? "**:**" : time(effect.getDuration())});
            }
        }
        if (speed.get()) right.add(new String[]{"Speed", String.format("%.1f b/s", blocksPerSecond)});
        if (ping.get()) right.add(new String[]{"Ping", latency() + " ms"});
        if (tps.get()) right.add(new String[]{"TPS", tpsCount == 0 ? "..." : String.format("%.1f", averageTps())});

        if (right.isEmpty()) return;

        int inner = 0;
        for (String[] l : right) inner = Math.max(inner, DOT + 4 + Ui.width(l[0]) + LABEL_GAP + Ui.width(l[1]));
        int boxWidth = inner + PADDING * 2, boxHeight = right.size() * LINE + PADDING * 2 - (LINE - Ui.FONT_HEIGHT);
        float x1 = width - MARGIN, y1 = height - MARGIN, x0 = x1 - boxWidth, y0 = y1 - boxHeight;
        Ui.surface(g, x0, y0, x1, y1, RADIUS);
        float y = y0 + PADDING;
        for (int i = 0; i < right.size(); i++) {
            String[] l = right.get(i);
            label(g, l[0], x0 + PADDING, y, i);
            Ui.text(g, l[1], x1 - PADDING - Ui.width(l[1]), y, Ui.TEXT);
            y += LINE;
        }
        bottomRightHeight = boxHeight + MARGIN * 2;
    }

    /** A label row: accent dot, grey label. Returns where the label ends. */
    private static float label(GuiGraphics g, String text, float x, float y, int index) {
        float cy = y + Ui.FONT_HEIGHT / 2f;
        Ui.rect(g, x, cy - DOT / 2f, x + DOT, cy + DOT / 2f, DOT / 2f, Ui.gradient(index * 0.05f));
        Ui.text(g, text, x + DOT + 4, y, Ui.MUTED);
        return x + DOT + 4 + Ui.width(text);
    }

    private void drawCoords(GuiGraphics g, float y) {
        double x = mc.player.getX(), py = mc.player.getY(), z = mc.player.getZ();
        boolean nether = mc.level.dimension() == Level.NETHER;
        double scale = nether ? NETHER_SCALE : 1 / NETHER_SCALE;
        String here = String.format("%.0f %.0f %.0f", x, py, z);
        String other = mc.level.dimension() == Level.END ? "" : String.format("  %.0f %.0f", x * scale, z * scale);
        int labelWidth = DOT + 4 + Ui.width("XYZ"), hereWidth = Ui.width(here);
        float x0 = MARGIN, x1 = x0 + PADDING * 2 + labelWidth + LABEL_GAP + hereWidth + Ui.width(other);
        Ui.surface(g, x0, y, x1, y + BOX_HEIGHT, RADIUS);
        float ty = y + (BOX_HEIGHT - Ui.FONT_HEIGHT) / 2f;
        float tx = label(g, "XYZ", x0 + PADDING, ty, 0) + LABEL_GAP;
        Ui.text(g, here, tx, ty, Ui.TEXT);
        Ui.text(g, other, tx + hereWidth, ty, Ui.MUTED);
    }

    private void drawArmor(GuiGraphics g, int x, int y) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            g.renderItem(stack, x, y);
            g.renderItemDecorations(mc.font, stack, x, y);
            x += ITEM + 1;
        }
    }

    private double averageTps() {
        double sum = 0;
        for (int i = 0; i < tpsCount; i++) sum += tpsSamples[i];
        return sum / tpsCount;
    }

    private int latency() {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static String time(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
