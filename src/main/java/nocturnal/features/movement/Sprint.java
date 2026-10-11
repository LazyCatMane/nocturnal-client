package nocturnal.features.movement;

import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;

public class Sprint extends Module {
    public enum Mode { LEGIT, OMNI }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Legit sprints forward only, Omni stays sprinting in every direction and when standing still", Mode.LEGIT));

    public Sprint() {
        super("Sprint", "Sprints automatically", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (inGame() && mode.get() == Mode.LEGIT) mc.options.keySprint.setDown(true);
    }

    /** Vanilla stops sprinting earlier in the tick when not moving forward, so re-apply it right before sending. */
    @Subscribe
    private void onPreMotion(Events.PreMotion e) {
        if (mode.get() == Mode.OMNI && mc.player.getFoodData().getFoodLevel() > 6) mc.player.setSprinting(true);
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}
