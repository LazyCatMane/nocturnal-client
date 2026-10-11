package nocturnal.features.misc;

import nocturnal.command.Command;
import nocturnal.module.Modules;
import nocturnal.util.Chat;

public class LayoutCommand extends Command {
    public LayoutCommand() {
        super("layout", "<save|clear|show>", "Sets InventoryManager's hotbar layout from your current hotbar");
    }

    @Override
    public void run(String[] args) {
        if (args.length != 1) throw usageError();
        InventoryManager manager = Modules.get(InventoryManager.class);
        switch (args[0].toLowerCase()) {
            case "save" -> {
                if (mc.player == null) throw new Failure("Join a world first");
                manager.saveLayout();
                Chat.info("Layout saved: " + manager.layoutText());
            }
            case "clear" -> {
                manager.clearLayout();
                Chat.info("Layout cleared");
            }
            case "show" -> Chat.info("Layout: " + manager.layoutText());
            default -> throw usageError();
        }
    }
}
