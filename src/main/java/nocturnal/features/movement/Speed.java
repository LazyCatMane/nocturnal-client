package nocturnal.features.movement;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;

/**
 * Strafe keeps full speed in any direction, even in the air. Bhop jumps automatically and boosts each hop, then
 * decays like NCP expects. OnGround walks faster without jumping. Off while sneaking.
 */
public class Speed extends Module {
    public enum Mode { STRAFE, BHOP, ONGROUND }

    /** Sprinting speed on flat ground, in blocks per tick. */
    private static final double SPRINT_SPEED = 0.2873;
    /** How fast hop speed falls back toward base: sharply the tick after a jump, slowly after that. */
    private static final double AFTER_JUMP_DECAY = 0.66, AIR_DECAY = 1 / 159.0;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Strafe, Bhop or OnGround", Mode.STRAFE));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Multiplier on sprint speed", 1.2, 0.5, 3, 0.05));
    private final NumberSetting hopBoost = add(new NumberSetting("Hop boost", "Bhop: extra speed on each jump", 1.6, 1, 2.5, 0.05));

    private double lastDist;
    /** 1 on the jump tick, 2 on the tick after, 0 otherwise. */
    private int hopStage;

    public Speed() {
        super("Speed", "Move faster", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Override
    protected void onEnable() {
        lastDist = 0;
        hopStage = 0;
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (e.packet instanceof ClientboundPlayerPositionPacket) lastDist = 0;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mode.get() != Mode.BHOP || !active() || !mc.player.onGround() || !moving()) return;
        mc.player.jumpFromGround();
        hopStage = 1;
    }

    @Subscribe
    private void onTickPost(Events.TickPost e) {
        if (inGame()) lastDist = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        if (!active() || !moving()) return;
        double base = baseSpeed();
        double s = switch (mode.get()) {
            case STRAFE -> Math.max(e.movement.horizontalDistance(), base);
            case BHOP -> {
                double hop;
                if (hopStage == 1) hop = base * hopBoost.get();
                else if (hopStage == 2) hop = lastDist - AFTER_JUMP_DECAY * (lastDist - base);
                else if (mc.player.onGround()) hop = base;
                else hop = lastDist - lastDist * AIR_DECAY;
                if (hopStage > 0) hopStage = hopStage == 1 ? 2 : 0;
                yield Math.max(hop, base);
            }
            case ONGROUND -> mc.player.onGround() ? base : -1;
        };
        if (s < 0) return;

        Vec2 input = mc.player.input.getMoveVector();
        double rad = Math.toRadians(mc.player.getYRot());
        Vec3 dir = new Vec3(-Math.sin(rad) * input.y + Math.cos(rad) * input.x, 0, Math.cos(rad) * input.y + Math.sin(rad) * input.x).normalize();
        e.movement = new Vec3(dir.x * s, e.movement.y, dir.z * s);
        mc.player.setDeltaMovement(e.movement.x, mc.player.getDeltaMovement().y, e.movement.z);
    }

    private boolean active() {
        return !mc.player.isShiftKeyDown() && !mc.player.isPassenger() && !mc.player.isFallFlying() && !mc.player.getAbilities().flying;
    }

    private boolean moving() {
        return mc.player.input.getMoveVector().lengthSquared() > 0;
    }

    private double baseSpeed() {
        double base = SPRINT_SPEED * speed.get();
        var effect = mc.player.getEffect(MobEffects.SPEED);
        if (effect != null) base *= 1 + 0.2 * (effect.getAmplifier() + 1);
        return base;
    }
}
