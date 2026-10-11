package nocturnal.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.Packet;

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
