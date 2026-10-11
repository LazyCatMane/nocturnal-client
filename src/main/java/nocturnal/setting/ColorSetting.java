package nocturnal.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** ARGB color, written as #RRGGBB or #AARRGGBB. */
public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    @Override
    public String display() {
        return String.format("#%08X", value);
    }

    @Override
    public boolean parse(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6 && hex.length() != 8) return false;
        try {
            int argb = (int) Long.parseLong(hex, 16);
            set(hex.length() == 6 ? 0xFF000000 | argb : argb);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(display());
    }

    @Override
    public void fromJson(JsonElement json) {
        parse(json.getAsString());
    }
}
