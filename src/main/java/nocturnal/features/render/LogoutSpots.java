package nocturnal.features.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.ColorSetting;
import nocturnal.util.Combat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Marks where players were standing when they left the server, with their health and the time. */
public class LogoutSpots extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private record Spot(String name, AABB box, float health, String time) {}

    private final ColorSetting color = add(new ColorSetting("Color", "Box and text color", 0xFFFF5555));

    /** Players in render distance as of the last tick, so we know where someone was when they log out. */
    private final Map<UUID, Spot> seen = new HashMap<>();
    private final Map<UUID, Spot> spots = new HashMap<>();
    private ClientLevel level;

    public LogoutSpots() {
        super("LogoutSpots", "Marks where players logged out", Category.RENDER);
    }

    @Override
    public String info() {
        return spots.isEmpty() ? null : String.valueOf(spots.size());
    }

    @Override
    protected void onDisable() {
        seen.clear();
        spots.clear();
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame()) return;
        if (mc.level != level) {
            level = mc.level;
            onDisable();
        }
        seen.clear();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            seen.put(p.getUUID(), new Spot(p.getName().getString(), p.getBoundingBox(), Combat.health(p), null));
            spots.remove(p.getUUID());
        }
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (!(e.packet instanceof ClientboundPlayerInfoRemovePacket packet)) return;
        mc.execute(() -> {
            for (UUID id : packet.profileIds()) {
                Spot last = seen.get(id);
                if (last != null) spots.put(id, new Spot(last.name, last.box, last.health, LocalTime.now().format(TIME)));
            }
        });
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        int c = color.get();
        for (Spot s : spots.values()) {
            Gizmos.cuboid(s.box, GizmoStyle.stroke(c, 2)).setAlwaysOnTop();
            String text = s.name + " " + Math.round(s.health) + "hp " + s.time;
            Gizmos.billboardText(text, s.box.getBottomCenter().add(0, s.box.getYsize() + 0.5, 0), TextGizmo.Style.forColorAndCentered(c)).setAlwaysOnTop();
        }
    }
}
