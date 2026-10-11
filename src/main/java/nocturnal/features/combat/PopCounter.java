package nocturnal.features.combat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Counts totem pops per player. Nametags shows the count next to their name. */
public class PopCounter extends Module {
    private final Map<UUID, Integer> pops = new ConcurrentHashMap<>();
    private ClientLevel level;

    public PopCounter() {
        super("PopCounter", "Counts totem pops per player on their nametag", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        pops.clear();
    }

    public int pops(Player p) {
        return pops.getOrDefault(p.getUUID(), 0);
    }

    public void record(Player p) {
        if (isEnabled()) pops.merge(p.getUUID(), 1, Integer::sum);
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (!(e.packet instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
        mc.execute(() -> {
            if (mc.level != null && packet.getEntity(mc.level) instanceof Player p) record(p);
        });
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (mc.level != level) {
            level = mc.level;
            pops.clear();
        }
    }
}
