package nocturnal.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.values = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public void cycle(int direction) {
        set(values[Math.floorMod(value.ordinal() + direction, values.length)]);
    }

    @Override
    public boolean parse(String text) {
        for (E e : values) {
            if (e.name().equalsIgnoreCase(text)) {
                set(e);
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value.name());
    }

    @Override
    public void fromJson(JsonElement json) {
        String name = json.getAsString();
        for (E e : values) if (e.name().equals(name)) value = e;
    }
}
