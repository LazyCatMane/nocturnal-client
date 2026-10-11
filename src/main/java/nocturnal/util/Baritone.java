package nocturnal.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.world.phys.Vec3;

/**
 * The only place that touches Baritone. Every method is a no-op when Baritone isn't installed.
 * Baritone types must only appear inside Api, so this class still loads without Baritone.
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
        if (AVAILABLE) Api.goTo(x, z);
    }

    public static void goTo(int x, int y, int z) {
        if (AVAILABLE) Api.goTo(x, y, z);
    }

    /** Paths toward a point far away in the given direction. */
    public static void walkDirection(Vec3 origin, float yaw, double distance) {
        if (AVAILABLE) Api.walkDirection(origin, yaw, distance);
    }

    public static void stop() {
        if (AVAILABLE) Api.stop();
    }

    public static boolean isPathing() {
        return AVAILABLE && Api.isPathing();
    }

    /** Runs a Baritone command without the # prefix, e.g. "mine diamond_ore". */
    public static void command(String command) {
        if (AVAILABLE) Api.command(command);
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

        static void path(Goal goal) {
            primary().getCustomGoalProcess().setGoalAndPath(goal);
        }

        static void goTo(int x, int z) {
            path(new GoalXZ(x, z));
        }

        static void goTo(int x, int y, int z) {
            path(new GoalBlock(x, y, z));
        }

        static void walkDirection(Vec3 origin, float yaw, double distance) {
            path(GoalXZ.fromDirection(origin, yaw, distance));
        }

        static void stop() {
            primary().getPathingBehavior().cancelEverything();
        }

        static boolean isPathing() {
            return primary().getPathingBehavior().isPathing();
        }

        static void command(String command) {
            primary().getCommandManager().execute(command);
        }

        static void applyPreset() {
            Settings s = BaritoneAPI.getSettings();
            s.antiCheatCompatibility.value = true;
            s.allowInventory.value = true;
            s.chatControl.value = false;
        }
    }
}
