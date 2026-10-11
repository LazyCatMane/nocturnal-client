package nocturnal.event;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Listeners are compiled into direct lambdas once per method, so posting an event costs
 * the same as a normal method call. Safe to post from the network thread.
 */
public final class EventBus {
    private record Handler(Class<?> event, BiConsumer<Object, Object> invoker, int priority) {}
    private record Listener(Object owner, Handler handler) {}

    private static final Map<Class<?>, List<Handler>> HANDLER_CACHE = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void register(Object owner) {
        for (Handler handler : handlers(owner.getClass())) {
            List<Listener> list = listeners.computeIfAbsent(handler.event, k -> new CopyOnWriteArrayList<>());
            list.add(new Listener(owner, handler));
            list.sort(Comparator.comparingInt((Listener l) -> l.handler.priority).reversed());
        }
    }

    public void unregister(Object owner) {
        for (List<Listener> list : listeners.values()) list.removeIf(l -> l.owner == owner);
    }

    public boolean hasListeners(Class<?> event) {
        List<Listener> list = listeners.get(event);
        return list != null && !list.isEmpty();
    }

    public <E> E post(E event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list != null) {
            for (Listener l : list) l.handler.invoker.accept(l.owner, event);
        }
        return event;
    }

    private static List<Handler> handlers(Class<?> type) {
        return HANDLER_CACHE.computeIfAbsent(type, t -> {
            List<Handler> found = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    Subscribe sub = m.getAnnotation(Subscribe.class);
                    if (sub == null) continue;
                    if (m.getParameterCount() != 1 || Modifier.isStatic(m.getModifiers())) {
                        throw new IllegalStateException("@Subscribe method must be non-static with one parameter: " + m);
                    }
                    found.add(new Handler(m.getParameterTypes()[0], compile(m), sub.priority()));
                }
            }
            return found;
        });
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<Object, Object> compile(Method m) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), MethodHandles.lookup());
            MethodHandle target = lookup.unreflect(m);
            return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                lookup, "accept",
                MethodType.methodType(BiConsumer.class),
                MethodType.methodType(void.class, Object.class, Object.class),
                target,
                MethodType.methodType(void.class, m.getDeclaringClass(), m.getParameterTypes()[0])
            ).getTarget().invokeExact();
        } catch (Throwable e) {
            throw new IllegalStateException("Could not compile listener " + m, e);
        }
    }
}
