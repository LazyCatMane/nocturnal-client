package nocturnal.features.research;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;
import nocturnal.util.LogFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Records every server position correction with the movement state that caused it, to
 * nocturnal/research/setbacks.csv. Open it in a spreadsheet to find what the anticheat allows.
 */
public class SetbackLogger extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final NumberSetting maxDistance = add(new NumberSetting("Max distance", "Corrections farther than this are treated as teleports and ignored", 50, 1, 500, 1));

    private final LogFile file = new LogFile("research/setbacks.csv",
        "time,x,y,z,distance,speed_bps,motion_y,on_ground,sprinting,elytra,in_water,move_packets_last_second,ping");
    private final ConcurrentLinkedDeque<Long> movePackets = new ConcurrentLinkedDeque<>();
    private int count;

    public SetbackLogger() {
        super("SetbackLogger", "Logs rubberbands and what you were doing to a CSV", Category.RESEARCH);
    }

    @Override
    protected void onEnable() {
        count = 0;
        movePackets.clear();
        file.open();
    }

    @Override
    protected void onDisable() {
        file.close();
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (!(e.packet instanceof ServerboundMovePlayerPacket)) return;
        long now = System.currentTimeMillis();
        movePackets.add(now);
        while (!movePackets.isEmpty() && now - movePackets.peekFirst() > 1000) movePackets.pollFirst();
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        LocalPlayer p = mc.player;
        if (!(e.packet instanceof ClientboundPlayerPositionPacket packet) || p == null) return;

        Vec3 target = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(p), packet.change(), packet.relatives()).position();
        double distance = p.position().distanceTo(target);
        if (distance > maxDistance.get()) return;

        Vec3 motion = p.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z) * 20;
        int ping = mc.getConnection() == null || mc.getConnection().getPlayerInfo(p.getUUID()) == null
            ? 0 : mc.getConnection().getPlayerInfo(p.getUUID()).getLatency();

        count++;
        file.write(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.3f,%.2f,%.3f,%b,%b,%b,%b,%d,%d",
            LocalDateTime.now().format(TIME), p.getX(), p.getY(), p.getZ(), distance, speed, motion.y,
            p.onGround(), p.isSprinting(), p.isFallFlying(), p.isInWater(), movePackets.size(), ping));
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        file.flush();
    }

    @Override
    public String info() {
        return String.valueOf(count);
    }
}
