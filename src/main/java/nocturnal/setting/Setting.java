package nocturnal.setting;

import com.google.gson.JsonElement;
import nocturnal.config.Config;

/** A saved, GUI-editable and command-editable value. Subclasses only define parsing and JSON. */
public abstract class Setting<T> {
    public final String name;
    public final String description;
    protected final T defaultValue;
    protected T value;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
        Config.markDirty();
    }

    public void reset() {
        set(defaultValue);
    }

    public String display() {
        return String.valueOf(value);
    }

    /** Parses command input. Returns false if the text is not a valid value. */
    public abstract boolean parse(String text);

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);
}
