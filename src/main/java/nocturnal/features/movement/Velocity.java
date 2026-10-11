package nocturnal.features.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.EntityMotionPacketAccessor;
import nocturnal.mixin.ExplodePacketAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;

import java.util.Optional;

public class Velocity extends Module {
    public enum Mode { CANCEL, PERCENT }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Cancel ignores knockback, Percent scales it", Mode.PERCENT));
    private final NumberSetting horizontal = add(new NumberSetting("Horizontal", "Percent of horizontal knockback to keep", 0, 0, 100, 1));
    private final NumberSetting vertical = add(new NumberSetting("Vertical", "Percent of vertical knockback to keep", 0, 0, 100, 1));
    private final BoolSetting explosions = add(new BoolSetting("Explosions", "Also reduce explosion knockback", true));

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get() == Mode.CANCEL ? "Cancel" : horizontal.getInt() + "% " + vertical.getInt() + "%";
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (e.packet instanceof ClientboundSetEntityMotionPacket packet && packet.getId() == p.getId()) {
            if (mode.get() == Mode.CANCEL) {
                e.cancel();
                return;
            }
            Vec3 now = p.getDeltaMovement();
            Vec3 knockback = packet.getMovement().subtract(now);
            ((EntityMotionPacketAccessor) (Object) packet).nocturnal$setMovement(now.add(scale(knockback)));
        } else if (explosions.get() && e.packet instanceof ClientboundExplodePacket packet && packet.playerKnockback().isPresent()) {
            Optional<Vec3> knockback = mode.get() == Mode.CANCEL ? Optional.empty() : packet.playerKnockback().map(this::scale);
            ((ExplodePacketAccessor) (Object) packet).nocturnal$setPlayerKnockback(knockback);
        }
    }

    private Vec3 scale(Vec3 knockback) {
        double h = horizontal.get() / 100, v = vertical.get() / 100;
        return new Vec3(knockback.x * h, knockback.y * v, knockback.z * h);
    }
}
