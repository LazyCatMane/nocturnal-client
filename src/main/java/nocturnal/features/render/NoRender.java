package nocturnal.features.render;

import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

import java.util.EnumMap;
import java.util.Map;

public class NoRender extends Module {
    private final Map<Events.HideKind, BoolSetting> hide = new EnumMap<>(Events.HideKind.class);

    public NoRender() {
        super("NoRender", "Stops distracting or laggy things from drawing", Category.RENDER);
        option(Events.HideKind.FOG, "Fog", "Distance, water and lava fog");
        option(Events.HideKind.WEATHER, "Weather", "Rain and snow");
        option(Events.HideKind.TOTEM, "Totem", "Totem pop animation and particles");
        option(Events.HideKind.EXPLOSIONS, "Explosions", "Explosion particles and block debris");
        option(Events.HideKind.HURT_CAM, "Hurt cam", "Camera shake when you take damage");
        option(Events.HideKind.OVERLAYS, "Overlays", "Fire, water, in-wall, pumpkin and powder snow overlays");
    }

    private void option(Events.HideKind kind, String name, String description) {
        hide.put(kind, add(new BoolSetting(name, description, true)));
    }

    @Subscribe
    private void onHide(Events.Hide e) {
        BoolSetting option = hide.get(e.kind);
        if (option != null && option.get()) e.cancel();
    }
}
