package nocturnal.features.misc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.ChatComponentAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.setting.StringSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Spam filter, timestamps, "(x3)" instead of repeated lines, and a longer chat history. */
public class ChatUtils extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BoolSetting filter = add(new BoolSetting("Filter", "Hide messages containing any filter word", true));
    private final StringSetting words = add(new StringSetting("Filter words", "Comma separated, not case sensitive", "discord.gg/, has joined the queue"));
    private final BoolSetting stack = add(new BoolSetting("Stack", "Collapse repeated messages into one line with a count", true));
    private final BoolSetting timestamps = add(new BoolSetting("Timestamps", "Show the time before each message", true));
    private final NumberSetting history = add(new NumberSetting("History", "Chat lines kept (vanilla is 100)", 1000, 100, 10000, 100));

    private String lastText;
    private int repeats;

    public ChatUtils() {
        super("ChatUtils", "Chat filter, timestamps, stacking and longer history", Category.MISC);
    }

    @Subscribe
    private void onChat(Events.ChatReceive e) {
        String text = e.message.getString();
        if (filter.get() && matchesFilter(text)) {
            e.cancel();
            return;
        }

        Component message = e.message;
        if (stack.get() && text.equals(lastText)) {
            repeats++;
            List<GuiMessage> messages = ((ChatComponentAccessor) mc.gui.getChat()).nocturnal$messages();
            if (!messages.isEmpty()) {
                messages.removeFirst();
                ((ChatComponentAccessor) mc.gui.getChat()).nocturnal$refresh();
            }
            message = message.copy().append(Component.literal(" (x" + repeats + ")").withStyle(ChatFormatting.GRAY));
        } else {
            lastText = text;
            repeats = 1;
        }

        if (timestamps.get()) message = Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY).append(message);
        e.message = message;
    }

    @Subscribe
    private void onHistory(Events.ChatHistory e) {
        e.limit = history.getInt();
    }

    private boolean matchesFilter(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : words.get().split(",")) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && lower.contains(w)) return true;
        }
        return false;
    }
}
