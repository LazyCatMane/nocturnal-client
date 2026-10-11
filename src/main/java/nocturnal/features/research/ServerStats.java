package nocturnal.features.research;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.features.render.ClickGui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;

import java.util.concurrent.ConcurrentLinkedDeque;

/** TPS, ping, setbacks per minute and a "server not responding" timer, top left. */
public class ServerStats extends Module {
    private static final int SAMPLES = 5;

    private final NumberSetting lagThreshold = add(new NumberSetting("Lag threshold", "Seconds without packets before the lag timer shows", 1, 0.5, 10, 0.5));

    private final double[] tpsSamples = new double[SAMPLES];
    private int sampleCount, sampleIndex;
    private long lastTimePacket;
    private volatile long lastPacket;
    private final ConcurrentLinkedDeque<Long> setbacks = new ConcurrentLinkedDeque<>();

    public ServerStats() {
        super("ServerStats", "Shows TPS, ping, setbacks per minute and lag", Category.RESEARCH);
    }

    @Override
    protected void onEnable() {
        sampleCount = sampleIndex = 0;
        lastTimePacket = 0;
        lastPacket = System.currentTimeMillis();
        setbacks.clear();
    }

    @Subscribe
    private void onPacket(Events.PacketReceive e) {
        long now = System.currentTimeMillis();
        lastPacket = now;
        if (e.packet instanceof ClientboundSetTimePacket) {
            long interval = now - lastTimePacket;
            lastTimePacket = now;
            // The server sends the time every 20 ticks, so 1000 ms between packets means 20 TPS.
            if (interval > 0 && interval < 10_000) {
                tpsSamples[sampleIndex] = Math.min(20, 20_000.0 / interval);
                sampleIndex = (sampleIndex + 1) % SAMPLES;
                sampleCount = Math.min(SAMPLES, sampleCount + 1);
            }
        } else if (e.packet instanceof ClientboundPlayerPositionPacket) {
            setbacks.add(now);
        }
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (!inGame() || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        while (!setbacks.isEmpty() && now - setbacks.peekFirst() > 60_000) setbacks.pollFirst();

        int y = 2;
        y = line(e, y, "TPS", sampleCount == 0 ? "..." : String.format("%.1f", tps()));
        y = line(e, y, "Ping", ping() + " ms");
        y = line(e, y, "Setbacks/min", String.valueOf(setbacks.size()));
        double silent = (now - lastPacket) / 1000.0;
        if (silent >= lagThreshold.get()) line(e, y, "Lag", String.format("%.1fs", silent));
    }

    @Override
    public String info() {
        return sampleCount == 0 ? null : String.format("%.1f", tps());
    }

    private double tps() {
        double sum = 0;
        for (int i = 0; i < sampleCount; i++) sum += tpsSamples[i];
        return sum / sampleCount;
    }

    private int ping() {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private int line(Events.Render2D e, int y, String label, String value) {
        e.graphics.drawString(mc.font, label + ": ", 2, y, ClickGui.accent(), true);
        e.graphics.drawString(mc.font, value, 2 + mc.font.width(label + ": "), y, 0xFFFFFFFF, true);
        return y + mc.font.lineHeight + 1;
    }
}
