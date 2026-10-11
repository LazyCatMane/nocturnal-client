package nocturnal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;

/**
 * Server-side rotations. A module asks to face somewhere for one tick; the movement packet carries that rotation,
 * the action runs right after it is sent, and your view never moves. The highest priority request each tick wins.
 */
public final class Rotations {
    private static final Minecraft mc = Minecraft.getInstance();

    private static boolean requested, applied;
    private static float yaw, pitch, realYaw, realPitch;
    private static int priority;
    private static Runnable action;

    /** The rotation the server last received from us. */
    private static float serverYaw, serverPitch;

    public static void rotate(Vec3 target, int priority, Runnable action) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        rotate(yaw, pitch, priority, action);
    }

    public static void rotate(float yaw, float pitch, int priority, Runnable action) {
        if (requested && priority < Rotations.priority) return;
        requested = true;
        Rotations.yaw = yaw;
        Rotations.pitch = Mth.clamp(pitch, -90, 90);
        Rotations.priority = priority;
        Rotations.action = action;
    }

    public static float serverYaw() {
        return serverYaw;
    }

    public static float serverPitch() {
        return serverPitch;
    }

    /** True once something this tick already claimed rotations at the given priority or above. */
    public static boolean isClaimed(int priority) {
        return requested && Rotations.priority >= priority;
    }

    /** Snaps a rotation onto the mouse sensitivity grid, the way real mouse movement lands. */
    private static float onMouseGrid(float from, float to) {
        double f = mc.options.sensitivity().get() * 0.6 + 0.2;
        double step = f * f * f * 8.0 * 0.15;
        return (float) (from + Math.round(Mth.wrapDegrees(to - from) / step) * step);
    }

    @Subscribe(priority = -1000)
    private void onPreMotion(Events.PreMotion e) {
        if (!requested || mc.player == null) return;
        realYaw = mc.player.getYRot();
        realPitch = mc.player.getXRot();
        mc.player.setYRot(onMouseGrid(serverYaw, yaw));
        mc.player.setXRot(Mth.clamp(onMouseGrid(serverPitch, pitch), -90, 90));
        applied = true;
    }

    @Subscribe(priority = 1000)
    private void onPostMotion(Events.PostMotion e) {
        if (applied) {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
        Runnable run = requested ? action : null;
        clear();
        if (run != null) run.run();
    }

    @Subscribe
    private void onTickPost(Events.TickPost e) {
        clear();
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (e.packet instanceof ServerboundMovePlayerPacket p && p.hasRotation()) {
            serverYaw = p.getYRot(serverYaw);
            serverPitch = p.getXRot(serverPitch);
        }
    }

    private static void clear() {
        requested = applied = false;
        action = null;
    }
}
