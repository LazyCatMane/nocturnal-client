package nocturnal.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of registry entries such as blocks or items, written as ids ("netherrack, minecraft:obsidian").
 * Commands and the GUI replace the whole list; commands also take "add <ids>", "remove <ids>" and "clear".
 */
public class ListSetting<T> extends Setting<Set<T>> {
    private final Registry<T> registry;

    @SafeVarargs
    public ListSetting(String name, String description, Registry<T> registry, T... defaults) {
        super(name, description, Collections.unmodifiableSet(new LinkedHashSet<>(List.of(defaults))));
        this.registry = registry;
    }

    public boolean contains(T entry) {
        return value.contains(entry);
    }

    @Override
    public String display() {
        if (value.isEmpty()) return "none";
        List<String> ids = new ArrayList<>();
        for (T entry : value) {
            Identifier id = registry.getKey(entry);
            ids.add(id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString());
        }
        return String.join(", ", ids);
    }

    @Override
    public boolean parse(String text) {
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("clear") || lower.equals("none")) {
            set(Collections.emptySet());
            return true;
        }
        boolean add = lower.startsWith("add "), remove = lower.startsWith("remove ");
        if (add || remove) trimmed = trimmed.substring(trimmed.indexOf(' ') + 1);

        Set<T> entries = new LinkedHashSet<>();
        for (String word : trimmed.split("[,\\s]+")) {
            if (word.isEmpty()) continue;
            T entry = lookup(word);
            if (entry == null) return false;
            entries.add(entry);
        }
        Set<T> next = new LinkedHashSet<>(add || remove ? value : Set.of());
        if (remove) next.removeAll(entries);
        else next.addAll(entries);
        set(Collections.unmodifiableSet(next));
        return true;
    }

    private T lookup(String word) {
        Identifier id = Identifier.tryParse(word.toLowerCase());
        return id == null ? null : registry.getOptional(id).orElse(null);
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (T entry : value) array.add(registry.getKey(entry).toString());
        return array;
    }

    @Override
    public void fromJson(JsonElement json) {
        Set<T> entries = new LinkedHashSet<>();
        for (JsonElement e : json.getAsJsonArray()) {
            T entry = lookup(e.getAsString());
            if (entry != null) entries.add(entry);
        }
        value = Collections.unmodifiableSet(entries);
    }
}
