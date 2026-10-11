package nocturnal.module;

import net.minecraft.client.Minecraft;
import nocturnal.Nocturnal;
import nocturnal.config.Config;
import nocturnal.setting.Setting;
import nocturnal.util.Chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for every feature. Put a subclass anywhere under nocturnal.features and it is found,
 * registered, shown in the ClickGUI, controllable by commands and saved automatically.
 * Listeners (@Subscribe methods) only receive events while the module is enabled.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String description;
    public final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private boolean enabledByDefault;
    private int defaultKey = -1;
    private int key = -1;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Call in the constructor to have the module on when no saved config exists. */
    protected void enabledByDefault() {
        enabledByDefault = true;
    }

    /** Call in the constructor to give the module a keybind when no saved config exists. */
    protected void defaultKey(int key) {
        defaultKey = key;
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) {
            Nocturnal.BUS.register(this);
            onEnable();
        } else {
            onDisable();
            Nocturnal.BUS.unregister(this);
        }
        Config.markDirty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public int key() {
        return key;
    }

    public int defaultKey() {
        return defaultKey;
    }

    public void setKey(int key) {
        this.key = key;
        Config.markDirty();
    }

    /** Text shown next to the name in the module list HUD, or null. */
    public String info() {
        return null;
    }

    protected static boolean inGame() {
        return mc.player != null && mc.level != null;
    }

    protected void log(String message) {
        Chat.info(name + ": " + message);
    }
}
