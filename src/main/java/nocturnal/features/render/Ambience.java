package nocturnal.features.render;

import net.minecraft.world.attribute.EnvironmentAttributes;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Chat;
import nocturnal.util.SkyBackground;

import java.io.IOException;
import java.util.Locale;

/** Custom sky, fog and cloud colors, an animated sky background and a fixed time of day. Only you see it; the server's time is unchanged. */
public class Ambience extends Module {
    /** Each one is assets/nocturnal/sky/(name in lower case).mjpeg. */
    public enum Background { OFF, STORM }

    private final EnumSetting<Background> background = add(new EnumSetting<>("Background", "Animated sky; replaces the sky color in the overworld and End", Background.STORM));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "Background brightness", 1, 0.1, 1, 0.05));
    private final BoolSetting celestials = add(new BoolSetting("Sun and moon", "Draw the sun, moon and stars over the background", false));
    private final BoolSetting sky = add(new BoolSetting("Sky", "Replace the sky color", true));
    private final ColorSetting skyColor = add(new ColorSetting("Sky color", "Sky color", 0xFF2A1240));
    private final BoolSetting fog = add(new BoolSetting("Fog", "Replace the fog color", true));
    private final ColorSetting fogColor = add(new ColorSetting("Fog color", "Fog color", 0xFF3B1A5C));
    private final BoolSetting clouds = add(new BoolSetting("Clouds", "Replace the cloud color", false));
    private final ColorSetting cloudColor = add(new ColorSetting("Cloud color", "Cloud color", 0xFFC9A8F0));
    private final BoolSetting time = add(new BoolSetting("Time", "Lock the time of day", true));
    private final NumberSetting timeOfDay = add(new NumberSetting("Time of day", "0 sunrise, 6000 noon, 12000 sunset, 18000 midnight", 18000, 0, 23999, 500));

    private SkyBackground loaded;
    private Background loadedKind = Background.OFF;

    public Ambience() {
        super("Ambience", "Custom sky, fog and time of day", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        unload();
    }

    @Subscribe
    private void onSky(Events.SkyRender e) {
        if (e.part == Events.SkyPart.SKY) {
            SkyBackground bg = background();
            if (bg == null) return;
            bg.draw(brightness.get().floatValue());
            e.cancel();
        } else if (loaded != null && (e.part == Events.SkyPart.DARK_DISC || !celestials.get())) {
            e.cancel();
        }
    }

    @Subscribe
    private void onEnvironment(Events.EnvironmentValue e) {
        if (sky.get() && e.attribute == EnvironmentAttributes.SKY_COLOR) e.value = skyColor.get();
        else if (fog.get() && e.attribute == EnvironmentAttributes.FOG_COLOR) e.value = fogColor.get();
        else if (clouds.get() && e.attribute == EnvironmentAttributes.CLOUD_COLOR) e.value = cloudColor.get();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (time.get() && inGame()) mc.level.getLevelData().setDayTime(timeOfDay.getInt());
    }

    /** Loads the chosen background the first time it's drawn after a change. Returns null when it's off or failed. */
    private SkyBackground background() {
        Background want = background.get();
        if (want != loadedKind) {
            unload();
            loadedKind = want;
            if (want != Background.OFF) {
                try {
                    loaded = new SkyBackground(want.name().toLowerCase(Locale.ROOT));
                } catch (IOException ex) {
                    Chat.error("Ambience: " + ex.getMessage());
                }
            }
        }
        return loaded;
    }

    private void unload() {
        if (loaded != null) loaded.close();
        loaded = null;
        loadedKind = Background.OFF;
    }
}
