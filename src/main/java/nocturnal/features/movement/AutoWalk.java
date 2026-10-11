package nocturnal.features.movement;

import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;
import nocturnal.util.Baritone;

public class AutoWalk extends Module {
    public enum Mode { SIMPLE, BARITONE }

    private static final double BARITONE_DISTANCE = 30_000_000;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Simple holds forward, Baritone paths in your facing direction", Mode.SIMPLE));
    private Mode running;

    public AutoWalk() {
        super("AutoWalk", "Walks forward automatically", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame()) return;
        if (mode.get() != running) {
            stop();
            running = mode.get();
            if (running == Mode.BARITONE) {
                if (!Baritone.available()) {
                    log("Baritone is not installed");
                    setEnabled(false);
                    return;
                }
                Baritone.walkDirection(mc.player.position(), mc.player.getYRot(), BARITONE_DISTANCE);
            }
        }
        if (running == Mode.SIMPLE) mc.options.keyUp.setDown(true);
    }

    @Override
    protected void onDisable() {
        stop();
        running = null;
    }

    private void stop() {
        if (running == Mode.SIMPLE) mc.options.keyUp.setDown(false);
        if (running == Mode.BARITONE) Baritone.stop();
    }
}
