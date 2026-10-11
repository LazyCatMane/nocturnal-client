package nocturnal.features.movement;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;

/**
 * Solid makes still water and lava surfaces walkable. Dolphin floats you up and bobs on the surface instead.
 * Both float you up when you're in the liquid. Sneak to sink.
 */
public class Jesus extends Module {
    public enum Mode { SOLID, DOLPHIN }

    private static final double RISE_SPEED = 0.11;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Solid walks on the surface, Dolphin bobs on it", Mode.SOLID));
    private final BoolSetting lava = add(new BoolSetting("Lava", "Also work on lava", true));

    public Jesus() {
        super("Jesus", "Walk on water and lava", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Subscribe
    private void onStand(Events.StandOnFluid e) {
        if (mode.get() != Mode.SOLID || mc.player.isShiftKeyDown()) return;
        if (e.fluid.is(FluidTags.WATER) || (lava.get() && e.fluid.is(FluidTags.LAVA))) e.cancel();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.player.isShiftKeyDown() || mc.player.isPassenger()) return;
        if (!mc.player.isInWater() && !(lava.get() && mc.player.isInLava())) return;
        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(v.x, Math.max(v.y, RISE_SPEED), v.z);
    }
}
