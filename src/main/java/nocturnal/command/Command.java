package nocturnal.command;

import net.minecraft.client.Minecraft;

/** Base for chat commands. Put a subclass anywhere under nocturnal.features and it registers itself. */
public abstract class Command {
    protected static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String usage;
    public final String description;

    protected Command(String name, String usage, String description) {
        this.name = name;
        this.usage = usage;
        this.description = description;
    }

    public abstract void run(String[] args);

    /** Throw from run() to show a red error message to the user. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) {
            super(message, null, false, false);
        }
    }

    protected Failure usageError() {
        return new Failure("Usage: " + name + " " + usage);
    }
}
