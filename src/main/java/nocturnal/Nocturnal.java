package nocturnal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import nocturnal.command.Command;
import nocturnal.command.Commands;
import nocturnal.config.Config;
import nocturnal.event.EventBus;
import nocturnal.event.Events;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.util.Baritone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Nocturnal implements ClientModInitializer {
    public static final String ID = "nocturnal";
    public static final Logger LOG = LoggerFactory.getLogger("Nocturnal");
    public static final EventBus BUS = new EventBus();

    private static final String FEATURE_INDEX = "nocturnal/features.txt";

    @Override
    public void onInitializeClient() {
        List<Class<?>> classes = scan();
        Modules.init(instantiate(classes, Module.class));
        Commands.init(instantiate(classes, Command.class));
        Config.load();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> BUS.post(Events.TickPre.INSTANCE));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            BUS.post(Events.TickPost.INSTANCE);
            Config.tick();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "hud"), (graphics, delta) -> {
            Events.Render2D e = Events.Render2D.INSTANCE;
            e.graphics = graphics;
            e.tickDelta = delta.getGameTimeDeltaPartialTick(true);
            BUS.post(e);
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(Commands::onChat);
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Baritone.applyPreset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        LOG.info("Loaded {} modules", Modules.all().size());
    }

    /** Loads every class listed in nocturnal/features.txt, which the build generates from nocturnal/features. */
    private static List<Class<?>> scan() {
        ClassLoader loader = Nocturnal.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(FEATURE_INDEX)) {
            if (in == null) throw new IllegalStateException(FEATURE_INDEX + " is missing, rebuild with Gradle");
            List<Class<?>> classes = new ArrayList<>();
            for (String name : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!name.isBlank()) classes.add(Class.forName(name.trim(), true, loader));
            }
            return classes;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Could not load features", e);
        }
    }

    private static <T> List<T> instantiate(List<Class<?>> classes, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Class<?> c : classes) {
            if (!type.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers()) || c.isAnnotationPresent(Disabled.class)) continue;
            try {
                found.add(type.cast(c.getDeclaredConstructor().newInstance()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(c.getName() + " needs a public no-argument constructor", e);
            }
        }
        return found;
    }
}
