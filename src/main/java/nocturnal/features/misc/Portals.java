package nocturnal.features.misc;

import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GUI keeps chat and inventories open while standing in a portal. God mode stops confirming the teleport after a
 * dimension change: the server keeps you invulnerable while it waits, but also ignores your movement, so stand still.
 * Turn God mode or the module off to confirm and carry on.
 */
public class Portals extends Module {
    private final BoolSetting gui = add(new BoolSetting("GUI", "Use screens like chat and inventory inside portals", true));
    private final BoolSetting godMode = add(new BoolSetting("God mode", "Stay invulnerable after going through a portal until you turn this off", false));

    private final Queue<ServerboundAcceptTeleportationPacket> held = new ConcurrentLinkedQueue<>();
    private volatile boolean armed;
    private boolean releasing;

    public Portals() {
        super("Portals", "Screens in portals and portal god mode", Category.MISC);
    }

    @Override
    public String info() {
        return armed && !held.isEmpty() ? "God" : null;
    }

    @Override
    protected void onDisable() {
        release();
    }

    @Subscribe
    private void onPortalScreen(Events.PortalScreen e) {
        if (gui.get()) e.cancel();
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (e.packet instanceof ClientboundRespawnPacket && godMode.get()) armed = true;
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (releasing || !armed || !(e.packet instanceof ServerboundAcceptTeleportationPacket p)) return;
        e.cancel();
        held.add(p);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (armed && !godMode.get()) release();
    }

    private void release() {
        armed = false;
        if (mc.getConnection() == null) {
            held.clear();
            return;
        }
        releasing = true;
        try {
            for (ServerboundAcceptTeleportationPacket p; (p = held.poll()) != null; ) mc.getConnection().send(p);
        } finally {
            releasing = false;
        }
    }
}
