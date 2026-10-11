package nocturnal.command;

import net.minecraft.client.Minecraft;
import nocturnal.Nocturnal;
import nocturnal.config.Config;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.Setting;
import nocturnal.util.Chat;
import nocturnal.util.Keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses chat lines starting with the prefix. Besides named commands, every module is a command:
 * "<module>" toggles, "<module> <setting>" shows a value, "<module> <setting> <value>" sets it.
 */
public final class Commands {
    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    private Commands() {}

    public static void init(List<Command> found) {
        List<Command> all = new ArrayList<>(builtins());
        all.addAll(found);
        all.sort(Comparator.comparing(c -> c.name));
        for (Command c : all) COMMANDS.put(c.name.toLowerCase(), c);
    }

    /** Fabric chat hook: returning false stops the message from reaching the server. */
    public static boolean onChat(String message) {
        if (!message.startsWith(Config.prefix())) return true;
        Minecraft.getInstance().gui.getChat().addRecentChat(message);
        run(message.substring(Config.prefix().length()).trim());
        return false;
    }

    public static void run(String line) {
        if (line.isEmpty()) return;
        String[] parts = line.split("\\s+");
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        try {
            Command command = COMMANDS.get(parts[0].toLowerCase());
            if (command != null) {
                command.run(args);
                return;
            }
            Module module = Modules.byName(parts[0]);
            if (module == null) throw new Command.Failure("Unknown command or module. Try " + Config.prefix() + "help");
            runModule(module, args);
        } catch (Command.Failure e) {
            Chat.error(e.getMessage());
        } catch (RuntimeException e) {
            Chat.error("Command crashed: " + e);
            Nocturnal.LOG.error("Command '{}' failed", line, e);
        }
    }

    private static void runModule(Module module, String[] args) {
        if (args.length == 0) {
            module.toggle();
            Chat.info(module.name + (module.isEnabled() ? " on" : " off"));
            return;
        }
        Setting<?> setting = setting(module, args[0]);
        if (args.length == 1) {
            Chat.info(module.name + " " + setting.name + " = " + setting.display() + " (" + setting.description + ")");
            return;
        }
        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (!setting.parse(value)) throw new Command.Failure("Invalid value for " + setting.name + ": " + value);
        Chat.info(module.name + " " + setting.name + " = " + setting.display());
    }

    private static Setting<?> setting(Module module, String name) {
        String wanted = Modules.normalize(name);
        for (Setting<?> s : module.settings()) if (Modules.normalize(s.name).equals(wanted)) return s;
        StringBuilder names = new StringBuilder();
        for (Setting<?> s : module.settings()) names.append(' ').append(Modules.normalize(s.name));
        throw new Command.Failure(module.name + " has no setting '" + name + "'. Settings:" + names);
    }

    private static Module module(String name) {
        Module m = Modules.byName(name);
        if (m == null) throw new Command.Failure("No module named " + name);
        return m;
    }

    private static Command builtin(String name, String usage, String description, Consumer<String[]> action) {
        return new Command(name, usage, description) {
            @Override
            public void run(String[] args) {
                action.accept(args);
            }
        };
    }

    private static List<Command> builtins() {
        return List.of(
            builtin("help", "", "Lists commands", args -> {
                String p = Config.prefix();
                for (Command c : COMMANDS.values()) Chat.info(p + c.name + " " + c.usage + " - " + c.description);
                Chat.info(p + "<module> [setting] [value] - toggle a module, or view/change a setting");
            }),
            builtin("toggle", "<module>", "Toggles a module", args -> {
                if (args.length != 1) throw COMMANDS.get("toggle").usageError();
                runModule(module(args[0]), new String[0]);
            }),
            builtin("bind", "<module> <key|none>", "Binds a module to a key", args -> {
                if (args.length < 2) throw COMMANDS.get("bind").usageError();
                Module m = module(args[0]);
                String keyName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                int key = Keys.parse(keyName);
                if (key == -1 && !keyName.equalsIgnoreCase("none")) throw new Command.Failure("Unknown key: " + keyName);
                m.setKey(key);
                Chat.info(m.name + " bound to " + Keys.name(key));
            }),
            builtin("prefix", "<prefix>", "Changes the command prefix", args -> {
                if (args.length != 1) throw COMMANDS.get("prefix").usageError();
                Config.setPrefix(args[0]);
                Chat.info("Prefix set to " + args[0]);
            }),
            builtin("profile", "<list|save|load> [name]", "Manages config profiles", args -> {
                if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                    Chat.info("Profiles: " + String.join(", ", Config.profiles()) + " (active: " + Config.profile() + ")");
                } else if (args.length == 2 && args[0].equalsIgnoreCase("save")) {
                    Config.saveProfileAs(args[1]);
                    Chat.info("Saved profile " + args[1]);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("load")) {
                    if (!Config.loadProfile(args[1])) throw new Command.Failure("No profile named " + args[1]);
                    Chat.info("Loaded profile " + args[1]);
                } else {
                    throw COMMANDS.get("profile").usageError();
                }
            })
        );
    }
}
