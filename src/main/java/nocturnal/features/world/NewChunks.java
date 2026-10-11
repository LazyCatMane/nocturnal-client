package nocturnal.features.world;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import nocturnal.config.Config;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.ChunkMarks;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Tells chunks someone has been to before (old) from ones generated just now for us (new), and draws the old ones
 * as squares, so the trails players left are easy to follow. What it learns is saved per server and dimension in
 * nocturnal/newchunks, and the first verdict on a chunk sticks.
 *
 * Palette check: while the server generates a chunk, its block and biome palettes collect every entry that was ever
 * used, starting with air and plains. Saving the chunk rebuilds them from what's actually there, in the order the
 * blocks appear. So leftover entries, or entries out of that order, mean nobody has loaded the chunk before.
 * Credit to etianl (Trouser-Streak) and rfresh2 (XaeroPlus) for working this out.
 */
public class NewChunks extends Module {
    private static final byte NEW = 1, OLD = 2, OLD_VERSION = 3;
    private static final long SAVE_INTERVAL_MS = 60_000;
    private static final int OUTLINE_ALPHA = 0xC0;
    private static final float OUTLINE_WIDTH = 1.5f;
    /** Low sections are generated first and hold the most entries, so checking more rarely changes the verdict. */
    private static final int PALETTE_SECTIONS = 8;
    private static final Direction[] FLOWS_FROM = { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
    private static final Set<Block> NETHER_1_16 = Set.of(Blocks.NETHER_GOLD_ORE, Blocks.BLACKSTONE, Blocks.BASALT);
    private static final Set<Block> COPPER = Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);

    private final BoolSetting palette = add(new BoolSetting("Palette", "Read chunk palettes as the server sent them; reliable on 1.18+ servers such as 6b6t", true));
    private final BoolSetting liquid = add(new BoolSetting("Liquid flow", "For chunks the palette check can't decide: liquid already flowing means old, liquid starting to flow means new", true));
    private final BoolSetting oldVersions = add(new BoolSetting("Old versions", "Color old chunks generated before 1.17 (Overworld) or 1.16 (Nether) differently", true));
    private final ColorSetting oldColor = add(new ColorSetting("Old color", "Chunks someone has loaded before", 0x4022D3EE));
    private final ColorSetting versionColor = add(new ColorSetting("Version color", "Old chunks from before 1.17 (Overworld) or 1.16 (Nether)", 0x50FFB020));
    private final NumberSetting height = add(new NumberSetting("Height", "Y level to draw the squares at", 64, -64, 320, 1));
    private final NumberSetting range = add(new NumberSetting("Range", "How many chunks out to draw", 32, 4, 64, 1));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Draw each chunk's edges so single chunks are easy to pick out", true));
    private final BoolSetting throughWalls = add(new BoolSetting("Through walls", "Draw the squares over terrain instead of behind it", true));

    private ChunkMarks marks;
    private String marksFor;
    private long lastSave;

    public NewChunks() {
        super("NewChunks", "Shows chunks other players have been to, so you can follow their trails", Category.WORLD);
    }

    @Override
    protected void onDisable() {
        closeMarks();
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame()) return;
        openMarks();
        long now = System.currentTimeMillis();
        if (now - lastSave > SAVE_INTERVAL_MS) {
            marks.save();
            lastSave = now;
        }
    }

    @Subscribe
    private void onDisconnect(Events.Disconnect e) {
        closeMarks();
    }

    @Subscribe
    private void onChunkLoad(Events.ChunkLoad e) {
        if (!inGame()) return;
        openMarks();
        classify(e.chunk());
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (!liquid.get()) return;
        if (e.packet instanceof ClientboundBlockUpdatePacket p) {
            mc.execute(() -> liquidUpdate(p.getPos(), p.getBlockState()));
        } else if (e.packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            mc.execute(() -> p.runUpdates(this::liquidUpdate));
        }
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (marks == null || !inGame()) return;
        ChunkPos center = mc.player.chunkPosition();
        int r = range.getInt();
        double y = height.get();
        for (int x = center.x - r; x <= center.x + r; x++) {
            for (int z = center.z - r; z <= center.z + r; z++) {
                byte mark = marks.get(x, z);
                if (mark != OLD && mark != OLD_VERSION) continue;
                int color = mark == OLD_VERSION && oldVersions.get() ? versionColor.get() : oldColor.get();
                Vec3 min = new Vec3(x * 16, y, z * 16), max = new Vec3(x * 16 + 16, y, z * 16 + 16);
                GizmoStyle top = outline.get() ? GizmoStyle.strokeAndFill(ARGB.color(OUTLINE_ALPHA, color), OUTLINE_WIDTH, color) : GizmoStyle.fill(color);
                // Quads are only drawn from their front, so the square gets a second, downward-facing one.
                GizmoProperties upper = Gizmos.rect(min, max, Direction.UP, top);
                GizmoProperties lower = Gizmos.rect(min, max, Direction.DOWN, GizmoStyle.fill(color));
                if (throughWalls.get()) {
                    upper.setAlwaysOnTop();
                    lower.setAlwaysOnTop();
                }
            }
        }
    }

    /** Switches to the marks for the current server and dimension, checking chunks that loaded before we had them. */
    private void openMarks() {
        String id = worldId();
        if (id.equals(marksFor)) return;
        closeMarks();
        marks = new ChunkMarks(Config.DIR.resolve("newchunks").resolve(id + ".bin"));
        marksFor = id;
        lastSave = System.currentTimeMillis();
        int r = mc.options.getEffectiveRenderDistance();
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x - r; x <= center.x + r; x++) {
            for (int z = center.z - r; z <= center.z + r; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) classify(chunk);
            }
        }
    }

    private void closeMarks() {
        if (marks != null) marks.save();
        marks = null;
        marksFor = null;
    }

    private static String worldId() {
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
            : mc.getSingleplayerServer() != null ? "singleplayer " + mc.getSingleplayerServer().getWorldData().getLevelName()
            : "unknown";
        return fileName(server) + "/" + fileName(mc.level.dimension().identifier().toString());
    }

    private static String fileName(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void classify(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (marks.has(key)) return;
        byte mark = palette.get() ? paletteMark(chunk) : 0;
        if (mark == 0 && liquid.get() && hasFlowingLiquid(chunk)) mark = OLD;
        if (mark == OLD && fromOldVersion(chunk)) mark = OLD_VERSION;
        if (mark != 0) marks.put(key, mark);
    }

    /** Liquid starting to flow out of a source means the chunk was generated just now. */
    private void liquidUpdate(BlockPos pos, BlockState state) {
        if (marks == null || mc.level == null) return;
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty() || fluid.isSource()) return;
        long key = ChunkPos.asLong(pos);
        if (marks.has(key)) return;
        for (Direction d : FLOWS_FROM) {
            if (mc.level.getFluidState(pos.relative(d)).isSource()) {
                marks.put(key, NEW);
                return;
            }
        }
    }

    // Palette check

    private byte paletteMark(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        if (sections.length == 0 || !(sections[0].getBiomes() instanceof PalettedContainer<Holder<Biome>> biomes)) return 0;
        Predicate<Holder<Biome>> plains = b -> b.is(Biomes.PLAINS);
        boolean plainsListed = biomes.maybeHas(plains);
        if (mc.level.dimension() == Level.NETHER || mc.level.dimension() == Level.END) return plainsListed ? NEW : OLD;
        if (!plainsListed) return OLD;
        if (!contains(biomes.data, plains)) return NEW;
        // Real plains, so the biomes can't tell; fall back to the blocks.
        return blocksLookNew(sections) ? NEW : OLD;
    }

    private static boolean blocksLookNew(LevelChunkSection[] sections) {
        for (int i = 0; i < Math.min(sections.length, PALETTE_SECTIONS); i++) {
            PalettedContainer.Data<BlockState> data = sections[i].getStates().data;
            Palette<BlockState> p = data.palette();
            if (p.getSize() < 2) continue;
            // A linear palette keeps its order, so it gives a definite answer; a hash map palette only can by having extra entries.
            if (p instanceof LinearPalette) return outOfOrder(data);
            if (p instanceof HashMapPalette && hasUnusedEntries(data)) return true;
        }
        return false;
    }

    /** A rebuilt palette lists entries in the order they first appear, so each new index is the next one up. */
    private static boolean outOfOrder(PalettedContainer.Data<?> data) {
        int[] next = {0};
        boolean[] out = {false};
        data.storage().getAll(i -> {
            if (i == next[0]) next[0]++;
            else if (i > next[0]) out[0] = true;
        });
        return out[0];
    }

    private static boolean hasUnusedEntries(PalettedContainer.Data<?> data) {
        IntOpenHashSet used = new IntOpenHashSet();
        data.storage().getAll(used::add);
        return used.size() < data.palette().getSize();
    }

    private static <T> boolean contains(PalettedContainer.Data<T> data, Predicate<T> test) {
        boolean[] found = {false};
        data.storage().getAll(i -> {
            if (!found[0] && test.test(data.palette().valueFor(i))) found[0] = true;
        });
        return found[0];
    }

    // Liquid and version checks

    private static boolean hasFlowingLiquid(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir() && contains(section.getStates().data, NewChunks::isFlowing)) return true;
        }
        return false;
    }

    private static boolean isFlowing(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !fluid.isSource();
    }

    /**
     * Nether chunks from before 1.16 have no gold ore, blackstone or basalt. Overworld chunks from before 1.17 have
     * stone but no deepslate just above y 0, and no copper; anything below y 0 was added when the world was upgraded.
     * Old chunks' palettes only list what's present, so checking the palette is enough.
     */
    private static boolean fromOldVersion(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        if (mc.level.dimension() == Level.NETHER) {
            for (LevelChunkSection s : sections) if (s.getStates().maybeHas(b -> NETHER_1_16.contains(b.getBlock()))) return false;
            return true;
        }
        if (mc.level.dimension() != Level.OVERWORLD || chunk.getMinY() > 0) return false;
        LevelChunkSection ground = sections[chunk.getSectionIndex(0)];
        if (!ground.getStates().maybeHas(b -> b.is(Blocks.STONE)) || ground.getStates().maybeHas(b -> b.is(Blocks.DEEPSLATE))) return false;
        for (int i = chunk.getSectionIndex(0); i < sections.length; i++) {
            if (sections[i].getStates().maybeHas(b -> COPPER.contains(b.getBlock()))) return false;
        }
        return true;
    }
}
