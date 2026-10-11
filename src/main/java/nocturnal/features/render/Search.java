package nocturnal.features.render;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.ListSetting;
import nocturnal.setting.NumberSetting;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Highlights chosen blocks in loaded chunks. Chunks are scanned once when they load, skipping sections whose palette
 * can't contain a match, then kept up to date from block update packets.
 */
public class Search extends Module {
    private static final int SECTION = 16;

    private final ListSetting<Block> blocks = add(new ListSetting<>("Blocks", "Blocks to highlight", BuiltInRegistries.BLOCK,
        Blocks.SPAWNER, Blocks.ANCIENT_DEBRIS, Blocks.END_PORTAL_FRAME));
    private final ColorSetting color = add(new ColorSetting("Color", "Highlight color", 0xFF22D3EE));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Lines from your crosshair to each block", false));
    private final NumberSetting maxShown = add(new NumberSetting("Max shown", "Most blocks drawn at once, nearest first", 500, 50, 5000, 50));

    private final Map<Long, Set<BlockPos>> found = new HashMap<>();
    private Set<Block> scannedFor;

    public Search() {
        super("Search", "Highlights chosen blocks through walls", Category.RENDER);
    }

    @Override
    public String info() {
        int count = found.values().stream().mapToInt(Set::size).sum();
        return count == 0 ? null : String.valueOf(count);
    }

    @Override
    protected void onDisable() {
        found.clear();
        scannedFor = null;
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (inGame() && blocks.get() != scannedFor) rescanAll();
    }

    @Subscribe
    private void onChunkLoad(Events.ChunkLoad e) {
        scan(e.chunk());
    }

    @Subscribe
    private void onChunkUnload(Events.ChunkUnload e) {
        found.remove(e.chunk().getPos().toLong());
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (e.packet instanceof ClientboundBlockUpdatePacket p) {
            mc.execute(() -> update(p.getPos(), p.getBlockState()));
        } else if (e.packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            mc.execute(() -> p.runUpdates(this::update));
        }
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (!inGame() || found.isEmpty()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Vector3fc f = camera.forwardVector();
        Vec3 start = cam.add(f.x(), f.y(), f.z());
        int c = color.get();

        found.values().stream().flatMap(Set::stream)
            .sorted((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(cam), Vec3.atCenterOf(b).distanceToSqr(cam)))
            .limit(maxShown.getInt())
            .forEach(pos -> {
                Gizmos.cuboid(new AABB(pos), GizmoStyle.stroke(c, 2)).setAlwaysOnTop();
                if (tracers.get()) Gizmos.line(start, Vec3.atCenterOf(pos), c, 1.5F).setAlwaysOnTop();
            });
    }

    private void rescanAll() {
        found.clear();
        scannedFor = blocks.get();
        int radius = mc.options.getEffectiveRenderDistance();
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) scan(chunk);
            }
        }
    }

    private void scan(LevelChunk chunk) {
        Set<Block> targets = blocks.get();
        if (targets.isEmpty()) return;
        Set<BlockPos> hits = new HashSet<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> targets.contains(s.getBlock()))) continue;
            int minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < SECTION; y++) {
                for (int z = 0; z < SECTION; z++) {
                    for (int x = 0; x < SECTION; x++) {
                        if (targets.contains(section.getBlockState(x, y, z).getBlock())) hits.add(new BlockPos(minX + x, minY + y, minZ + z));
                    }
                }
            }
        }
        long key = chunk.getPos().toLong();
        if (hits.isEmpty()) found.remove(key);
        else found.put(key, hits);
    }

    private void update(BlockPos pos, BlockState state) {
        long key = ChunkPos.asLong(pos);
        if (blocks.contains(state.getBlock())) {
            found.computeIfAbsent(key, k -> new HashSet<>()).add(pos.immutable());
        } else {
            Set<BlockPos> set = found.get(key);
            if (set != null && set.remove(pos) && set.isEmpty()) found.remove(key);
        }
    }
}
