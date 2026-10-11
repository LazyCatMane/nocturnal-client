package nocturnal.event;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import nocturnal.Nocturnal;

/** Every event the client posts. Add new ones here and fire them from a mixin or Fabric callback. */
public final class Events {
    private Events() {}

    public abstract static class Cancellable {
        private boolean cancelled;

        public void cancel() { cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    /** Start of the client tick. */
    public static final class TickPre {
        public static final TickPre INSTANCE = new TickPre();
    }

    /** End of the client tick. */
    public static final class TickPost {
        public static final TickPost INSTANCE = new TickPost();
    }

    /** HUD drawing. Reused every frame, so don't keep a reference to it. */
    public static final class Render2D {
        public static final Render2D INSTANCE = new Render2D();
        public GuiGraphics graphics;
        public float tickDelta;
    }

    /** Right before the client sends its position, rotation and sprint state for this tick. */
    public static final class PreMotion {
        public static final PreMotion INSTANCE = new PreMotion();
    }

    /** Right after the movement packet for this tick was sent. */
    public static final class PostMotion {
        public static final PostMotion INSTANCE = new PostMotion();
    }

    /** World drawing. Add shapes with net.minecraft.gizmos.Gizmos in world coordinates; they last one frame. */
    public static final class Render3D {
        public static final Render3D INSTANCE = new Render3D();
        public float tickDelta;
    }

    /** An entity is being prepared for drawing. Set color to a non-zero RGB to give it a glow outline. */
    public static final class EntityOutline {
        public static final EntityOutline INSTANCE = new EntityOutline();
        public Entity entity;
        public int color;
    }

    public enum HideKind { FOG, WEATHER, TOTEM, EXPLOSIONS, HURT_CAM, OVERLAYS }

    /** Something is about to be drawn. Cancel to hide it. */
    public static final class Hide extends Cancellable {
        public final HideKind kind;
        public Hide(HideKind kind) { this.kind = kind; }

        /** Returns true if a listener wants this hidden. */
        public static boolean hidden(HideKind kind) {
            return Nocturnal.BUS.hasListeners(Hide.class) && Nocturnal.BUS.post(new Hide(kind)).isCancelled();
        }
    }

    /** The local player is about to move by its own input and physics. Replace movement to change where it goes. */
    public static final class PlayerMove {
        public static final PlayerMove INSTANCE = new PlayerMove();
        public Vec3 movement;
    }

    /** The mouse is turning the local player. Cancel to keep the player's view still. */
    public static final class Turn extends Cancellable {
        public final double yaw, pitch;
        public Turn(double yaw, double pitch) { this.yaw = yaw; this.pitch = pitch; }
    }

    public enum SlowdownKind { ITEM_USE, SNEAK, BLOCK_SPEED, STUCK_IN_BLOCK }

    /** The local player is about to be slowed down. Cancel to prevent it. */
    public static final class Slowdown extends Cancellable {
        public final SlowdownKind kind;
        public Slowdown(SlowdownKind kind) { this.kind = kind; }

        /** Returns true if a listener cancelled this slowdown. */
        public static boolean cancelled(SlowdownKind kind) {
            return Nocturnal.BUS.hasListeners(Slowdown.class) && Nocturnal.BUS.post(new Slowdown(kind)).isCancelled();
        }
    }

    /** Camera setup is starting. Set detached to render the camera away from the player. */
    public static final class CameraSetup {
        public static final CameraSetup INSTANCE = new CameraSetup();
        public boolean detached;
    }

    /** Camera setup finished. Move it with CameraAccessor. */
    public static final class CameraUpdate {
        public static final CameraUpdate INSTANCE = new CameraUpdate();
        public Camera camera;
        public float tickDelta;
    }

    /** A screen finished building its widgets. Add buttons with Fabric's Screens.getButtons(screen). */
    public record ScreenInit(Screen screen) {}

    /** Raw keyboard input. action: 0 release, 1 press, 2 repeat. */
    public record Key(int key, int action) {}

    /** Fired on the network thread. Hop to the main thread with mc.execute() before touching the world. */
    public static final class PacketReceive extends Cancellable {
        public final Packet<?> packet;
        public PacketReceive(Packet<?> packet) { this.packet = packet; }
    }

    /** Fired on whichever thread sends the packet, usually the main thread. */
    public static final class PacketSend extends Cancellable {
        public final Packet<?> packet;
        public PacketSend(Packet<?> packet) { this.packet = packet; }
    }
}
