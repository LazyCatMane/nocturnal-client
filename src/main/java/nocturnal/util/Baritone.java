package nocturnal.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IElytraProcess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The only place that touches Baritone. Every method is a no-op when Baritone isn't installed.
 * Baritone types must only appear inside Api, so this class still loads without Baritone.
 */
public final class Baritone {
    private static final boolean AVAILABLE = isPresent();
    private static final double NEXT_NODE = 8;

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

    /** Baritone's Nether elytra process (#elytra) is running. */
    public static boolean elytraFlying() {
        return AVAILABLE && Api.elytraFlying();
    }

    /** True when Baritone is executing a goal or an elytra flight. */
    public static boolean hasGoal() {
        return AVAILABLE && Api.hasGoal();
    }

    /**
     * Next node on Baritone's #elytra path. Null until nether-pathfinder publishes one.
     * Never the raw goal or a walk path.
     */
    public static Vec3 flyTarget() {
        return AVAILABLE ? Api.flyTarget() : null;
    }

    /** True once #elytra has a path ElytraFly can follow. */
    public static boolean hasFlyPath() {
        return AVAILABLE && Api.hasFlyPath();
    }

    /** True when the player has reached the last #elytra node. */
    public static boolean atGoal() {
        return AVAILABLE && Api.atGoal();
    }

    /**
     * Hands the current goal to #elytra and keeps that process alive.
     * Must not cancel Baritone entirely — that destroys the nether path.
     */
    public static void prepareFlight() {
        if (AVAILABLE) Api.prepareFlight();
    }

    /** Same as #elytra reset: rebuild the nether path to the current destination. */
    public static void resetElytra() {
        if (AVAILABLE) Api.resetElytra();
    }

    public static boolean nether() {
        var player = Minecraft.getInstance().player;
        return player != null && player.level().dimension() == Level.NETHER;
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

        static void goTo(int x, int z) {
            primary().getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
        }

        static void goTo(int x, int y, int z) {
            primary().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
        }

        static void walkDirection(Vec3 origin, float yaw, double distance) {
            primary().getCustomGoalProcess().setGoalAndPath(GoalXZ.fromDirection(origin, yaw, distance));
        }

        static void stop() {
            primary().getPathingBehavior().cancelEverything();
        }

        static boolean isPathing() {
            return primary().getPathingBehavior().isPathing();
        }

        static boolean elytraFlying() {
            return primary().getElytraProcess().isActive();
        }

        static boolean hasGoal() {
            return primary().getCustomGoalProcess().isActive()
                || elytraFlying()
                || primary().getPathingBehavior().isPathing();
        }

        static boolean hasFlyPath() {
            return !elytraPath().isEmpty();
        }

        static Vec3 flyTarget() {
            var player = Minecraft.getInstance().player;
            if (player == null) return null;
            List<BlockPos> path = elytraPath();
            keepPathFresh(path, player);
            if (!path.isEmpty()) {
                // Only leave the path once we've used the last node. A 3D distance check
                // was aborting climbs early and flattening Y, which stuttered vertically.
                if (!pathComplete() && nearest(path, player) >= path.size() - 1) {
                    return cruise(path.getLast().getY() + 0.5);
                }
                return nextNode(path, player);
            }
            return elytraFlying() ? cruise(player.getY()) : null;
        }

        static boolean atGoal() {
            var player = Minecraft.getInstance().player;
            if (player == null || !pathComplete()) return false;
            List<BlockPos> path = elytraPath();
            if (path.isEmpty()) return false;
            return player.distanceToSqr(Vec3.atCenterOf(path.getLast())) < 16;
        }

        static void prepareFlight() {
            // ElytraFly does not use rockets. Baritone otherwise emergency-lands at 0 fireworks.
            ignoreFireworkSafety();
            IElytraProcess elytra = primary().getElytraProcess();
            if (!elytra.isLoaded() || !nether()) return;
            Goal g = goal();
            if (g != null) {
                try {
                    if (!elytra.isActive() || !sameDestination(elytra.currentDestination(), g)) elytra.pathTo(g);
                } catch (IllegalArgumentException ignored) {
                    // Only GoalXZ and GoalBlock can be flown to.
                }
            }
            if (!elytra.isActive()) return;
            primary().getInputOverrideHandler().clearAllKeys();
            holdFireworkAttempt(elytra);
            // Drop the walker so it cannot fight #elytra. Do not cancelEverything — that kills the path.
            if (primary().getCustomGoalProcess().isActive()) primary().getCustomGoalProcess().onLostControl();
        }

        static void resetElytra() {
            IElytraProcess elytra = primary().getElytraProcess();
            if (elytra.isActive()) elytra.resetState();
        }

        static void command(String command) {
            primary().getCommandManager().execute(command);
        }

        static void applyPreset() {
            Settings s = BaritoneAPI.getSettings();
            s.antiCheatCompatibility.value = true;
            s.allowInventory.value = true;
            s.chatControl.value = false;
            s.elytraTermsAccepted.value = true;
            s.elytraAutoJump.value = false;
            s.elytraAllowEmergencyLand.value = false;
            s.elytraMinFireworksBeforeLanding.value = -1;
            s.elytraMinimumDurability.value = 1;
            // 6b6t seed is not the 2b2t default; predicted terrain would backtrack constantly.
            s.elytraPredictTerrain.value = false;
            ignoreFireworkSafety();
        }

        private static void ignoreFireworkSafety() {
            Settings s = BaritoneAPI.getSettings();
            s.elytraAllowEmergencyLand.value = false;
            s.elytraAutoJump.value = false;
            s.elytraMinFireworksBeforeLanding.value = -1;
            s.elytraMinimumDurability.value = 1;
        }

        private static Goal goal() {
            Goal current = primary().getCustomGoalProcess().mostRecentGoal();
            if (current != null) return current;
            return primary().getPathingBehavior().getGoal();
        }

        private static boolean sameDestination(BlockPos dest, Goal goal) {
            if (dest == null) return false;
            if (goal instanceof GoalXZ g) return dest.getX() == g.getX() && dest.getZ() == g.getZ();
            if (goal instanceof GoalBlock g) return dest.getX() == g.x && dest.getY() == g.y && dest.getZ() == g.z;
            return false;
        }

        /** Unfinished segment: keep the last node's height and move toward the destination XZ. */
        private static Vec3 cruise(double y) {
            BlockPos dest = primary().getElytraProcess().currentDestination();
            if (dest == null) return null;
            return new Vec3(dest.getX() + 0.5, y, dest.getZ() + 0.5);
        }

        private static Vec3 nextNode(List<BlockPos> path, Entity player) {
            int i = nearest(path, player);
            while (i < path.size() - 1 && player.distanceToSqr(Vec3.atCenterOf(path.get(i))) < NEXT_NODE * NEXT_NODE) i++;
            return Vec3.atCenterOf(path.get(i));
        }

        private static int nearest(List<BlockPos> path, Entity player) {
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < path.size(); i++) {
                double d = player.distanceToSqr(Vec3.atCenterOf(path.get(i)));
                if (d < best) {
                    best = d;
                    nearest = i;
                }
            }
            return nearest;
        }

        /**
         * Loom remaps Baritone's private names to a/b/c, so look up by type instead of by name.
         * IElytraProcess → ElytraBehavior → PathManager → NetherPath.
         */
        private static List<BlockPos> elytraPath() {
            Object manager = pathManager();
            if (manager == null) return List.of();
            Object path = fieldOfType(manager, "NetherPath");
            return path == null ? List.of() : toPositions(path);
        }

        private static Object pathManager() {
            IElytraProcess elytra = primary().getElytraProcess();
            if (!elytra.isActive()) return null;
            Object behavior = fieldOfType(elytra, "ElytraBehavior");
            return behavior == null ? null : fieldOfType(behavior, "PathManager");
        }

        /** First boolean on PathManager is completePath. False means more segments are still coming. */
        private static boolean pathComplete() {
            Object manager = pathManager();
            if (manager == null) return false;
            for (Field field : manager.getClass().getDeclaredFields()) {
                if (field.getType() != boolean.class) continue;
                field.setAccessible(true);
                try {
                    return field.getBoolean(manager);
                } catch (IllegalAccessException e) {
                    return false;
                }
            }
            return false;
        }

        /**
         * Tell PathManager we are still making progress so it does not freeze next-segment
         * calculation for 100 ticks ("no progress") while ElytraFly is the one moving.
         */
        private static void keepPathFresh(List<BlockPos> path, Entity player) {
            Object manager = pathManager();
            if (manager == null) return;
            int near = path.isEmpty() ? 0 : nearest(path, player);
            int ints = 0;
            for (Field field : manager.getClass().getDeclaredFields()) {
                if (field.getType() != int.class) continue;
                field.setAccessible(true);
                ints++;
                try {
                    if (ints == 2) field.setInt(manager, 0); // ticksNearUnchanged
                    if (ints == 3) field.setInt(manager, near); // playerNear
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        /** First int on ElytraBehavior is remainingFireworkTicks. Keep it high so Baritone skips rocket attempts. */
        private static void holdFireworkAttempt(IElytraProcess elytra) {
            Object behavior = fieldOfType(elytra, "ElytraBehavior");
            if (behavior == null) return;
            for (Field field : behavior.getClass().getDeclaredFields()) {
                if (field.getType() != int.class) continue;
                field.setAccessible(true);
                try {
                    field.setInt(behavior, 40);
                } catch (IllegalAccessException ignored) {
                }
                return;
            }
        }

        private static Object fieldOfType(Object owner, String typeName) {
            for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!field.getType().getName().contains(typeName)) continue;
                    field.setAccessible(true);
                    try {
                        return field.get(owner);
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                }
            }
            return null;
        }

        private static List<BlockPos> toPositions(Object path) {
            if (path == null) return List.of();
            List<BlockPos> out = new ArrayList<>();
            if (path instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) addPos(out, list.get(i));
            } else if (path instanceof Iterable<?> nodes) {
                for (Object node : nodes) addPos(out, node);
            }
            return out;
        }

        private static void addPos(List<BlockPos> out, Object node) {
            if (node instanceof BlockPos pos) {
                out.add(pos);
                return;
            }
            if (node == null) return;
            try {
                int x = ((Number) node.getClass().getField("x").get(node)).intValue();
                int y = ((Number) node.getClass().getField("y").get(node)).intValue();
                int z = ((Number) node.getClass().getField("z").get(node)).intValue();
                out.add(new BlockPos(x, y, z));
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
