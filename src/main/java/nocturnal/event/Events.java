package nocturnal.event;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import nocturnal.Nocturnal;
import nocturnal.module.Module;
import org.joml.Matrix4f;

import java.util.List;

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

    /** The world's projection for this frame, including view bobbing. Don't keep the matrix, copy it. */
    public static final class WorldProjection {
        public static final WorldProjection INSTANCE = new WorldProjection();
        public Matrix4f projection;
    }

    public enum HideKind { FOG, WEATHER, TOTEM, EXPLOSIONS, HURT_CAM, OVERLAYS, PLAYER_NAMETAGS }

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

    /** The vehicle we're controlling is about to move. Replace movement to change where it goes. */
    public static final class VehicleMove {
        public static final VehicleMove INSTANCE = new VehicleMove();
        public Entity vehicle;
        public Vec3 movement;
    }

    /** The local player is walking near a block edge. Cancel to stop at the edge as if sneaking. */
    public static final class StopAtEdge extends Cancellable {
        public static boolean cancelled() {
            return Nocturnal.BUS.hasListeners(StopAtEdge.class) && Nocturnal.BUS.post(new StopAtEdge()).isCancelled();
        }
    }

    /** The local player is above the surface of a still liquid. Cancel to let them stand on it. */
    public static final class StandOnFluid extends Cancellable {
        public final FluidState fluid;
        public StandOnFluid(FluidState fluid) { this.fluid = fluid; }

        public static boolean cancelled(FluidState fluid) {
            return Nocturnal.BUS.hasListeners(StandOnFluid.class) && Nocturnal.BUS.post(new StandOnFluid(fluid)).isCancelled();
        }
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

    /** A module was turned on or off, including when a config loads. */
    public record ModuleToggle(Module module, boolean enabled) {}

    /** We're starting or continuing to mine a block, before this tick's progress is computed. */
    public record Mining(BlockPos pos) {}

    /** We're about to attack an entity, before the held item is sent to the server. */
    public record Attack(Entity target) {}

    /** A container screen was clicked over a slot. Cancel to swallow the click. */
    public static final class SlotClick extends Cancellable {
        public final AbstractContainerScreen<?> screen;
        public final Slot slot;
        public final int button;
        public SlotClick(AbstractContainerScreen<?> screen, Slot slot, int button) { this.screen = screen; this.slot = slot; this.button = button; }
    }

    /** An item's tooltip is asking for a picture to show under its text. Set image to provide one. */
    public static final class TooltipImage {
        public static final TooltipImage INSTANCE = new TooltipImage();
        public ItemStack stack;
        public TooltipComponent image;
    }

    /** Tooltip text for an item is being built. Add or change lines. */
    public record TooltipLines(ItemStack stack, List<Component> lines) {}

    /** A chunk arrived from the server and is now in the world. */
    public record ChunkLoad(LevelChunk chunk) {}

    /** A chunk is about to be removed from the world. */
    public record ChunkUnload(LevelChunk chunk) {}

    /**
     * Chunk meshes are being built, on worker threads. Cancel to leave the block out; blocks that aren't hidden then
     * draw all their faces, so they show through the hidden ones.
     */
    public static final class BlockVisibility extends Cancellable {
        public final BlockState state;
        public BlockVisibility(BlockState state) { this.state = state; }

        public static boolean hidden(BlockState state) {
            return Nocturnal.BUS.hasListeners(BlockVisibility.class) && Nocturnal.BUS.post(new BlockVisibility(state)).isCancelled();
        }
    }

    /** A chat line is about to be added. Cancel to drop it, or replace message to change it. */
    public static final class ChatReceive extends Cancellable {
        public Component message;
        public ChatReceive(Component message) { this.message = message; }
    }

    /** How many chat lines to keep. Vanilla keeps 100. */
    public static final class ChatHistory {
        public static final ChatHistory INSTANCE = new ChatHistory();
        public int limit;
    }

    /** We're standing in a portal with a screen open. Cancel to keep the screen open. */
    public static final class PortalScreen extends Cancellable {
        public static boolean cancelled() {
            return Nocturnal.BUS.hasListeners(PortalScreen.class) && Nocturnal.BUS.post(new PortalScreen()).isCancelled();
        }
    }

    /** The sky, fog or clouds are asking for an environment value such as a color. Set value to override it. */
    public static final class EnvironmentValue {
        public static final EnvironmentValue INSTANCE = new EnvironmentValue();
        public EnvironmentAttribute<?> attribute;
        public Object value;
    }

    public enum SkyPart { SKY, DARK_DISC, CELESTIALS }

    /** Part of the overworld or End sky is about to be drawn. Cancel to skip it; draw your own sky in its place. */
    public static final class SkyRender extends Cancellable {
        public final SkyPart part;
        public SkyRender(SkyPart part) { this.part = part; }

        public static boolean cancelled(SkyPart part) {
            return Nocturnal.BUS.hasListeners(SkyRender.class) && Nocturnal.BUS.post(new SkyRender(part)).isCancelled();
        }
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
