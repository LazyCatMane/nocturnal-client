package nocturnal.features.movement;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;

/** Walks up full blocks. Packet mode also sends the in-between heights of a real jump for stricter anticheats. */
public class Step extends Module {
    public enum Mode { VANILLA, PACKET }

    private static final double HEIGHT = 1;
    /** Vanilla steps anything up to this on its own, so only taller steps need the jump packets. */
    private static final double VANILLA_STEP = 0.6;
    /** Heights a real jump passes through on its first two ticks. */
    private static final double[] JUMP_OFFSETS = { 0.41999998688698, 0.7531999805212 };

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Vanilla raises step height, Packet also fakes the jump on the way up", Mode.PACKET));

    private double lastX, lastY, lastZ;
    private boolean lastOnGround, hasLast;

    public Step() {
        super("Step", "Walk up full blocks", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Override
    protected void onEnable() {
        hasLast = false;
    }

    @Override
    protected void onDisable() {
        AttributeInstance step = stepHeight();
        if (step != null) step.setBaseValue(step.getAttribute().value().getDefaultValue());
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        AttributeInstance step = stepHeight();
        if (step != null && step.getBaseValue() != HEIGHT) step.setBaseValue(HEIGHT);
    }

    @Subscribe
    private void onPreMotion(Events.PreMotion e) {
        if (mode.get() != Mode.PACKET || !hasLast || !lastOnGround || !mc.player.onGround()) return;
        double rise = mc.player.getY() - lastY;
        if (rise <= VANILLA_STEP || rise > HEIGHT + 1e-3) return;
        for (double offset : JUMP_OFFSETS) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(lastX, lastY + offset, lastZ, false, true));
        }
    }

    @Subscribe
    private void onPostMotion(Events.PostMotion e) {
        lastX = mc.player.getX();
        lastY = mc.player.getY();
        lastZ = mc.player.getZ();
        lastOnGround = mc.player.onGround();
        hasLast = true;
    }

    private static AttributeInstance stepHeight() {
        return mc.player == null ? null : mc.player.getAttribute(Attributes.STEP_HEIGHT);
    }
}
