# Nocturnal

Standalone Fabric client for 6b6t. Minecraft 1.21.11, Mojang mappings, Java 21, optional Baritone.

## Build and run

- `gradlew build` puts the jar in `build/libs/`. Put it in `.minecraft/mods` with Fabric API (and Baritone if you want pathing).
- `gradlew runClient` starts a dev game with Baritone included.

In game: Right Shift opens the ClickGUI, `.help` lists commands.

## Adding a module

Create a class anywhere under `src/main/java/nocturnal/features/`. That's it: the build lists it, and the client registers it, adds it to the GUI, makes it a command and saves its settings.

```java
package nocturnal.features.world;

public class Example extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "How far to look", 64, 1, 256, 1));
    private final BoolSetting notify = add(new BoolSetting("Notify", "Chat message when found", true));

    public Example() {
        super("Example", "What it does, shown in the GUI", Category.WORLD);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame()) return;
        // logic
    }
}
```

- Settings: `BoolSetting`, `NumberSetting` (step 1 = integer), `EnumSetting`, `StringSetting`, `ColorSetting`.
- Listeners only run while the module is enabled. Events are in `event/Events.java`.
- `enabledByDefault()` / `defaultKey(GLFW.GLFW_KEY_X)` in the constructor set first-run defaults.
- Override `info()` to show text next to the name in the module list.
- Commands come free: `.example` toggles, `.example range` shows, `.example range 128` sets.
- `@Disabled` on the class removes it without deleting the file.

## Adding a command

Extend `Command` under `nocturnal/features/` and implement `run(String[] args)`. Throw `new Command.Failure("message")` for a red error.

## Adding an event

1. Add a class to `event/Events.java`.
2. Fire it with `Nocturnal.BUS.post(...)` from a Fabric callback in `Nocturnal.java` or a mixin in `mixin/` (register the mixin in `nocturnal.mixins.json`). Mixins only fire events, never contain feature logic.

## Adding a setting type

Extend `Setting<T>` in `setting/`. It works in commands and config immediately. In the GUI it gets a text field unless you add a case to `Widgets.forSetting()`.

## Layout

| Path | Contents |
| --- | --- |
| `Nocturnal.java` | Entrypoint, wires Fabric callbacks to events |
| `event/` | Event bus and all events |
| `setting/` | Setting types |
| `module/` | `Module` base, categories, registry |
| `command/` | `Command` base and built-in commands |
| `config/` | Saving and profiles |
| `gui/` | ClickGUI |
| `util/` | Baritone wrapper, chat, keys, log files |
| `mixin/` | Hooks that fire events |
| `features/` | Every module and command, by category |

## Files in `.minecraft/nocturnal/`

- `client.json`: prefix, active profile, GUI panel positions
- `profiles/<name>.json`: modules, keys, settings (`.profile save|load|list`)
- `research/setbacks.csv`, `research/packets.log`: research module output
