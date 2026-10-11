package nocturnal.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;

/**
 * The only place that touches Baritone. Every method is a no-op when Baritone isn't installed,
 * and Baritone classes are only loaded inside Api, so the client runs fine without it.
 */
public final class Baritone {
    private static final boolean AVAILABLE = isPresent();

    private Baritone() {}

    public static boolean available() {
        return AVAILABLE;
    }

    /** Settings that suit 6b6t. Applied once at startup, overriding Baritone's saved values. */
    public static void applyPreset() {
        if (AVAILABLE) Api.applyPreset();
    }

    public static void goTo(int x, int z) {
        if (AVAILABLE) Api.primary().getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
    }

    public static void goTo(int x, int y, int z) {
        if (AVAILABLE) Api.primary().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
    }

    public static void stop() {
        if (AVAILABLE) Api.primary().getPathingBehavior().cancelEverything();
    }

    public static boolean isPathing() {
        return AVAILABLE && Api.primary().getPathingBehavior().isPathing();
    }

    /** Runs a Baritone command without the # prefix, e.g. "mine diamond_ore". */
    public static void command(String command) {
        if (AVAILABLE) Api.primary().getCommandManager().execute(command);
    }

    private static boolean isPresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, Baritone.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final class Api {
        static IBaritone primary() {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        }

        static void applyPreset() {
            Settings s = BaritoneAPI.getSettings();
            s.antiCheatCompatibility.value = true;
            s.allowInventory.value = true;
            s.chatControl.value = false;
        }
    }
}
