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
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

import java.util.ArrayList;
import java.util.List;

/** Coordinates bottom left, armor beside the hotbar, and TPS, ping, speed and potion timers bottom right. */
public class Hud extends Module {
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final int VALUE_COLOR = 0xFFFFFFFF, DIM_COLOR = 0xFFAAAAAA;
    private static final int HOTBAR_HALF_WIDTH = 91, ITEM = 16, MARGIN = 2;
    private static final int TPS_SAMPLES = 5;
    private static final double NETHER_SCALE = 8;

    private final BoolSetting coords = add(new BoolSetting("Coords", "Your position, plus the matching Overworld/Nether position", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", "Your armor and its durability, right of the hotbar", true));
    private final BoolSetting tps = add(new BoolSetting("TPS", "Server ticks per second", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Your latency to the server", true));
    private final BoolSetting speed = add(new BoolSetting("Speed", "Horizontal speed in blocks per second", true));
    private final BoolSetting potions = add(new BoolSetting("Potions", "Active effects and time left", true));

    private final double[] tpsSamples = new double[TPS_SAMPLES];
    private int tpsCount, tpsIndex;
    private long lastTimePacket;
    private double blocksPerSecond;

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

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (!inGame() || mc.options.hideGui) return;
        GuiGraphics g = e.graphics;
        int width = g.guiWidth(), height = g.guiHeight(), line = mc.font.lineHeight + 1;

        if (coords.get()) drawCoords(g, height - line);
        if (armor.get()) drawArmor(g, width / 2 + HOTBAR_HALF_WIDTH + 4, height - ITEM - 3);

        List<String[]> right = new ArrayList<>();
        if (potions.get()) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                String name = effect.getEffect().value().getDisplayName().getString();
                if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);
                right.add(new String[]{name + " ", effect.isInfiniteDuration() ? "**:**" : time(effect.getDuration())});
            }
        }
        if (speed.get()) right.add(new String[]{"Speed ", String.format("%.1f b/s", blocksPerSecond)});
        if (ping.get()) right.add(new String[]{"Ping ", latency() + " ms"});
        if (tps.get()) right.add(new String[]{"TPS ", tpsCount == 0 ? "..." : String.format("%.1f", averageTps())});

        int y = height - line * right.size();
        for (String[] l : right) {
            int x = width - MARGIN - mc.font.width(l[0] + l[1]);
            g.drawString(mc.font, l[0], x, y, ClickGui.accent(), true);
            g.drawString(mc.font, l[1], x + mc.font.width(l[0]), y, VALUE_COLOR, true);
            y += line;
        }
    }

    private void drawCoords(GuiGraphics g, int y) {
        double x = mc.player.getX(), py = mc.player.getY(), z = mc.player.getZ();
        boolean nether = mc.level.dimension() == Level.NETHER;
        double scale = nether ? NETHER_SCALE : 1 / NETHER_SCALE;
        String here = String.format("%.0f %.0f %.0f", x, py, z);
        String other = mc.level.dimension() == Level.END ? "" : String.format(" [%.0f %.0f]", x * scale, z * scale);
        g.drawString(mc.font, "XYZ ", MARGIN, y, ClickGui.accent(), true);
        int vx = MARGIN + mc.font.width("XYZ ");
        g.drawString(mc.font, here, vx, y, VALUE_COLOR, true);
        g.drawString(mc.font, other, vx + mc.font.width(here), y, DIM_COLOR, true);
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
