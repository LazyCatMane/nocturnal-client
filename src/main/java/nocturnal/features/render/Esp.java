package nocturnal.features.render;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;

import java.util.ArrayList;
import java.util.List;

/** Highlights entities and storage through walls. Tracers reuses its targets and colors. */
public class Esp extends Module {
    public enum Mode { BOX, GLOW }

    private static final int STORAGE_RESCAN_TICKS = 20;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Box draws outlines, Glow uses the vanilla glowing effect on entities", Mode.BOX));
    private final BoolSetting players = add(new BoolSetting("Players", "Highlight players", true));
    private final ColorSetting playerColor = add(new ColorSetting("Player color", "", 0xFFFF5555));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Highlight hostile mobs", true));
    private final ColorSetting hostileColor = add(new ColorSetting("Hostile color", "", 0xFFFFAA00));
    private final BoolSetting items = add(new BoolSetting("Items", "Highlight dropped items", false));
    private final ColorSetting itemColor = add(new ColorSetting("Item color", "", 0xFF55FFFF));
    private final BoolSetting storage = add(new BoolSetting("Storage", "Highlight chests, ender chests, shulkers and barrels", true));
    private final ColorSetting storageColor = add(new ColorSetting("Storage color", "", 0xFFAA55FF));
    private final BoolSetting fill = add(new BoolSetting("Fill", "Box: also fill the box faintly", false));
    private final NumberSetting width = add(new NumberSetting("Line width", "Box outline thickness", 2, 1, 5, 0.5));

    private final List<BlockEntity> storageCache = new ArrayList<>();
    private long storageScannedAt = Long.MIN_VALUE;

    public Esp() {
        super("ESP", "See players, mobs, items and storage through walls", Category.RENDER);
    }

    @Override
    public String info() {
        return mode.get() == Mode.BOX ? "Box" : "Glow";
    }

    /** ARGB color for an entity ESP shows, or 0 if it isn't a target. */
    public int colorOf(Entity e) {
        if (e == mc.player || !e.isAlive()) return 0;
        if (e instanceof Player) return players.get() ? playerColor.get() : 0;
        if (e instanceof ItemEntity) return items.get() ? itemColor.get() : 0;
        if (Combat.isHostile(e)) return hostiles.get() ? hostileColor.get() : 0;
        return 0;
    }

    public int storageColor() {
        return storageColor.get();
    }

    /** Storage block entities in loaded chunks, rescanned once a second. Empty when Storage is off. */
    public List<BlockEntity> storage() {
        if (!storage.get() || mc.level == null) return List.of();
        long now = mc.level.getGameTime();
        if (now - storageScannedAt >= STORAGE_RESCAN_TICKS || now < storageScannedAt) {
            storageScannedAt = now;
            storageCache.clear();
            int radius = mc.options.getEffectiveRenderDistance();
            int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (isStorage(be)) storageCache.add(be);
                    }
                }
            }
        }
        return storageCache;
    }

    public static AABB boxOf(BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        VoxelShape shape = be.getBlockState().getShape(be.getLevel(), pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    public static AABB boxOf(Entity e, float tickDelta) {
        return e.getBoundingBox().move(e.getPosition(tickDelta).subtract(e.position()));
    }

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity
            || be instanceof ShulkerBoxBlockEntity || be instanceof BarrelBlockEntity;
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (!inGame()) return;
        if (mode.get() == Mode.BOX) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                int color = colorOf(entity);
                if (color != 0) box(boxOf(entity, e.tickDelta), color);
            }
        }
        for (BlockEntity be : storage()) box(boxOf(be), storageColor.get());
    }

    @Subscribe
    private void onOutline(Events.EntityOutline e) {
        if (mode.get() == Mode.GLOW) e.color = colorOf(e.entity);
    }

    private void box(AABB box, int color) {
        GizmoStyle style = fill.get()
            ? GizmoStyle.strokeAndFill(color, width.get().floatValue(), ARGB.color(48, color))
            : GizmoStyle.stroke(color, width.get().floatValue());
        Gizmos.cuboid(box, style).setAlwaysOnTop();
    }
}
