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
        option(Events.HideKind.ITEMS, "Dropped items", "Items on the ground, such as dumped piles");
        option(Events.HideKind.FALLING_BLOCKS, "Falling blocks", "Falling sand, gravel and anvils from lag machines");
        option(Events.HideKind.ARMOR_STANDS, "Armor stands", "Armor stands and what they hold");
        option(Events.HideKind.ITEM_FRAMES, "Item frames", "Item frames, glow item frames and the maps in them");
        option(Events.HideKind.FIREBALLS, "Fireballs", "Wither skulls, ghast and blaze fireballs, and dragon fireballs");
        option(Events.HideKind.BEACON_BEAMS, "Beacon beams", "The beam above beacons; the block stays");
        option(Events.HideKind.BANNERS, "Banners", "Standing and wall banners");
        option(Events.HideKind.ENCHANTING_BOOK, "Enchanting book", "The floating book above enchanting tables; the table stays");
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
