package nocturnal.features.misc;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.util.FormattedCharSequence;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.util.Chat;

import java.util.List;

/** Guards against things servers and players use to crash the client. */
public class AntiCrash extends Module {
    /** Minecraft crashes past 16,777,215 vertices in one text buffer; stay well below it. */
    private static final int TEXT_VERTEX_BUDGET = 4_000_000;
    private static final long WARN_INTERVAL_MS = 30_000;

    private final BoolSetting worldText = add(new BoolSetting("World text", "Stop drawing sign, text display and map label text for the rest of a frame once there's enough to crash the game", true));
    private final BoolSetting warn = add(new BoolSetting("Warn", "Say in chat when something was skipped to prevent a crash", true));

    private long lastWarning;

    public AntiCrash() {
        super("AntiCrash", "Protects against crash machines and crash exploits", Category.MISC);
        enabledByDefault();
    }

    @Subscribe
    private void onWorldText(Events.WorldText e) {
        if (!worldText.get()) return;
        List<SubmitNodeStorage.TextSubmit> submits = e.submits;
        long budget = TEXT_VERTEX_BUDGET;
        for (int i = 0; i < submits.size(); i++) {
            SubmitNodeStorage.TextSubmit t = submits.get(i);
            // Each glyph is a quad, drawn twice with a shadow and nine times with an outline; the background is one more.
            int perGlyph = t.outlineColor() != 0 ? 36 : t.dropShadow() ? 8 : 4;
            budget -= 4 + (long) glyphs(t.string(), budget / perGlyph + 1) * perGlyph;
            if (budget < 0) {
                int skipped = submits.size() - i;
                submits.subList(i, submits.size()).clear();
                warn("Skipped " + skipped + " world texts this frame that could have crashed the game");
                return;
            }
        }
    }

    /** Counts the glyphs in text, stopping early once there are more than max. */
    private static int glyphs(FormattedCharSequence text, long max) {
        int[] count = {0};
        text.accept((index, style, codePoint) -> ++count[0] <= max);
        return count[0];
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (!warn.get() || now - lastWarning < WARN_INTERVAL_MS) return;
        lastWarning = now;
        Chat.error(message);
    }
}
