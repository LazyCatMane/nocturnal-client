package nocturnal.features.render;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.OptionInstanceAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;

public class Fullbright extends Module {
    public enum Mode { GAMMA, NIGHT_VISION }

    private static final double GAMMA = 16;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Gamma has no side effects, Night vision adds the client-side effect", Mode.GAMMA));
    private double previousGamma = -1;

    public Fullbright() {
        super("Fullbright", "See in the dark", Category.RENDER);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (mode.get() == Mode.GAMMA) {
            removeNightVision();
            if (previousGamma < 0) previousGamma = mc.options.gamma().get();
            setGamma(GAMMA);
        } else {
            restoreGamma();
            if (inGame() && !mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false));
            }
        }
    }

    @Override
    protected void onDisable() {
        restoreGamma();
        removeNightVision();
    }

    private void restoreGamma() {
        if (previousGamma < 0) return;
        setGamma(previousGamma);
        previousGamma = -1;
    }

    private void removeNightVision() {
        if (inGame()) mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }

    private static void setGamma(double value) {
        ((OptionInstanceAccessor) (Object) mc.options.gamma()).nocturnal$setValue(value);
    }
}
