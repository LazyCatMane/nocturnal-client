package nocturnal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import nocturnal.Nocturnal;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * .minecraft/nocturnal/client.json holds client-wide state, profiles/<name>.json holds modules.
 * Loading skips anything unknown or missing, so adding or renaming settings never breaks a config.
 */
public final class Config {
    public static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("nocturnal");
    private static final Path PROFILES = DIR.resolve("profiles");
    private static final Path CLIENT = DIR.resolve("client.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_DELAY_TICKS = 40;

    public static final class PanelState {
        public int x, y;
        public boolean open = true;
    }

    private static final class ClientData {
        String prefix = ".";
        String profile = "default";
        Map<String, PanelState> panels = new HashMap<>();
    }

    private static ClientData client = new ClientData();
    private static boolean loading;
    private static int ticksUntilSave;

    private Config() {}

    public static String prefix() {
        return client.prefix;
    }

    public static void setPrefix(String prefix) {
        client.prefix = prefix;
        markDirty();
    }

    public static String profile() {
        return client.profile;
    }

    public static PanelState panel(String id, int defaultX, int defaultY) {
        return client.panels.computeIfAbsent(id, k -> {
            PanelState s = new PanelState();
            s.x = defaultX;
            s.y = defaultY;
            return s;
        });
    }

    /** Schedules a save. Repeated calls (e.g. dragging a slider) push the save back instead of writing each time. */
    public static void markDirty() {
        if (!loading) ticksUntilSave = SAVE_DELAY_TICKS;
    }

    public static void tick() {
        if (ticksUntilSave > 0 && --ticksUntilSave == 0) save();
    }

    public static void load() {
        JsonElement json = read(CLIENT);
        if (json != null) {
            ClientData data = GSON.fromJson(json, ClientData.class);
            if (data != null) client = data;
            if (client.panels == null) client.panels = new HashMap<>();
        }
        loadProfile(client.profile);
    }

    public static boolean loadProfile(String name) {
        JsonElement json = read(PROFILES.resolve(name + ".json"));
        if (json == null && !name.equals(client.profile)) return false;

        JsonObject root = json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
        loading = true;
        for (Module m : Modules.all()) {
            JsonObject o = root.has(m.name) ? root.getAsJsonObject(m.name) : new JsonObject();
            m.setKey(o.has("key") ? o.get("key").getAsInt() : m.defaultKey());
            JsonObject settings = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
            for (Setting<?> s : m.settings()) {
                s.reset();
                if (!settings.has(s.name)) continue;
                try {
                    s.fromJson(settings.get(s.name));
                } catch (RuntimeException e) {
                    Nocturnal.LOG.warn("Skipping invalid value for {}.{}", m.name, s.name);
                }
            }
            m.setEnabled(o.has("enabled") ? o.get("enabled").getAsBoolean() : m.isEnabledByDefault());
        }
        loading = false;
        client.profile = name;
        save();
        return true;
    }

    public static void saveProfileAs(String name) {
        client.profile = name;
        save();
    }

    public static List<String> profiles() {
        try (Stream<Path> files = Files.list(PROFILES)) {
            return files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json"))
                .map(n -> n.substring(0, n.length() - 5))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static void save() {
        ticksUntilSave = 0;
        JsonObject root = new JsonObject();
        for (Module m : Modules.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.key());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.settings()) settings.add(s.name, s.toJson());
            o.add("settings", settings);
            root.add(m.name, o);
        }
        write(PROFILES.resolve(client.profile + ".json"), root);
        write(CLIENT, GSON.toJsonTree(client));
    }

    private static JsonElement read(Path file) {
        if (!Files.exists(file)) return null;
        try {
            return JsonParser.parseString(Files.readString(file));
        } catch (Exception e) {
            Nocturnal.LOG.error("Could not read {}, using defaults", file, e);
            return null;
        }
    }

    private static void write(Path file, JsonElement json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Nocturnal.LOG.error("Could not save {}", file, e);
        }
    }
}
