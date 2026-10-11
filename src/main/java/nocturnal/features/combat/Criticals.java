package nocturnal.features.combat;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;
import nocturnal.util.Packets;

public class Criticals extends Module {
    public enum Mode { PACKET, JUMP }

    /** Height of the fake hop. The server counts any downward move while airborne as falling. */
    private static final double HOP = 0.0625;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode",
        "Packet fakes a tiny hop for every hit. Jump makes KillAura jump for real and hit on the way down", Mode.PACKET));

    public Criticals() {
        super("Criticals", "Makes your hits critical", Category.COMBAT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    /**
     * For KillAura: true when a hit now would crit (or can't crit at all, so waiting is pointless).
     * In Jump mode this starts a jump and returns false until we're falling.
     */
    public boolean readyToHit() {
        if (mode.get() == Mode.PACKET || !canCrit()) return true;
        if (mc.player.onGround()) {
            mc.player.jumpFromGround();
            return false;
        }
        return mc.player.fallDistance > 0 && mc.player.getDeltaMovement().y < 0;
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (mode.get() != Mode.PACKET || !(e.packet instanceof ServerboundInteractPacket p) || !inGame()) return;
        int id = Packets.attackTarget(p);
        if (id < 0 || !(mc.level.getEntity(id) instanceof LivingEntity) || !canCrit() || !mc.player.onGround()) return;

        boolean sprinting = mc.player.isSprinting();
        if (sprinting) mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        Vec3 pos = mc.player.position();
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + HOP, pos.z, false, mc.player.horizontalCollision));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, false, mc.player.horizontalCollision));
        if (sprinting) mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
    }

    /** Conditions under which the server never gives a critical, no matter how we fall. */
    private boolean canCrit() {
        return !mc.player.isInWater() && !mc.player.isInLava() && !mc.player.onClimbable() && !mc.player.isPassenger()
            && !mc.player.isFallFlying() && !mc.player.hasEffect(MobEffects.BLINDNESS);
    }
}
