package nocturnal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import nocturnal.features.render.ClickGui;

/** Client-side chat messages. Safe to call from any thread. */
public final class Chat {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ERROR = 0xFFFF5555;

    private Chat() {}

    public static void info(String message) {
        send(message, TEXT);
    }

    public static void error(String message) {
        send(message, ERROR);
    }

    private static void send(String message, int color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.getChat().addMessage(
            Component.literal("[Nocturnal] ").withColor(ClickGui.accent()).append(Component.literal(message).withColor(color))
        ));
    }
}
