package nocturnal.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public boolean parse(String text) {
        switch (text.toLowerCase()) {
            case "true", "on", "yes", "1" -> set(true);
            case "false", "off", "no", "0" -> set(false);
            case "toggle" -> toggle();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        value = json.getAsBoolean();
    }
}
