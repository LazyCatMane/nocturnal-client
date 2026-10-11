package nocturnal.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A number limited to [min, max] and rounded to step. A step of 1 makes it an integer setting. */
public class NumberSetting extends Setting<Double> {
    public final double min, max, step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    @Override
    public void set(Double v) {
        double rounded = Math.round(v / step) * step;
        super.set(Math.max(min, Math.min(max, rounded)));
    }

    @Override
    public String display() {
        return step >= 1 ? String.valueOf(getInt()) : String.format("%.2f", value);
    }

    @Override
    public boolean parse(String text) {
        try {
            set(Double.parseDouble(text));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        value = Math.max(min, Math.min(max, json.getAsDouble()));
    }
}
