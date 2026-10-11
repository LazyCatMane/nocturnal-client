package nocturnal.module;

import net.minecraft.client.Minecraft;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class Modules {
    private static List<Module> all = List.of();

    private Modules() {}

    public static void init(List<Module> found) {
        all = found.stream().sorted(Comparator.comparing(m -> m.name)).toList();
        Nocturnal.BUS.register(new Modules());
    }

    public static List<Module> all() {
        return all;
    }

    public static List<Module> in(Category category) {
        return all.stream().filter(m -> m.category == category).toList();
    }

    /** Case-insensitive, ignores spaces. */
    public static Module byName(String name) {
        String wanted = normalize(name);
        for (Module m : all) if (normalize(m.name).equals(wanted)) return m;
        return null;
    }

    public static <T extends Module> T get(Class<T> type) {
        for (Module m : all) if (m.getClass() == type) return type.cast(m);
        throw new IllegalArgumentException("Module not registered: " + type.getName());
    }

    public static String normalize(String s) {
        return s.replace(" ", "").toLowerCase();
    }

    @Subscribe
    private void onKey(Events.Key e) {
        if (e.action() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        for (Module m : all) if (m.key() == e.key()) m.toggle();
    }
}
