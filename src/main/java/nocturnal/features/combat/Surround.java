package nocturnal.features.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Interact;
import nocturnal.util.Packets;
import nocturnal.util.Rotations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Surrounds your feet with blast-proof blocks so crystals can't reach your legs. */
public class Surround extends Module {
    public enum Mode { STAY, ALWAYS }

    private static final int ROTATION_PRIORITY = 30;
    private static final double REACH = 4.5;
    private static final long REHIT_MS = 300;
    /** Blast resistance of obsidian. Anything at or above survives crystals. */
    private static final float BLAST_PROOF = 600;
    private static final int CENTER_TICKS = 10;
    private static final double CENTER_SPEED = 0.2, CENTERED = 0.05;
    /** Jumping more than this above where Stay mode started turns it off. */
    private static final double JUMP_TOLERANCE = 0.4;

    /** In order of preference. Valuable blast-proof blocks are left alone. */
    private static final List<Predicate<ItemStack>> BLOCKS = List.of(
        s -> s.is(Items.OBSIDIAN),
        s -> s.is(Items.CRYING_OBSIDIAN),
        s -> s.getItem() instanceof BlockItem b && b.getBlock().getExplosionResistance() >= BLAST_PROOF
            && !s.is(Items.ENDER_CHEST) && !s.is(Items.RESPAWN_ANCHOR) && !s.is(Items.NETHERITE_BLOCK) && !s.is(Items.ANCIENT_DEBRIS)
    );

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode",
        "Stay centers you on enable and turns off when you walk away or jump. Always surrounds wherever you stand still", Mode.STAY));
    private final NumberSetting height = add(new NumberSetting("Height", "Wall height. 2 also covers your head", 2, 1, 2, 1));
    private final NumberSetting blocksPerTick = add(new NumberSetting("Blocks per tick", "Most blocks placed in one tick", 4, 1, 8, 1));
    private final BoolSetting center = add(new BoolSetting("Center", "Stay: walk to the middle of the block first so 4 blocks are enough", true));
    private final BoolSetting floor = add(new BoolSetting("Floor", "Also place a block under you if it's missing", true));
    private final BoolSetting antiCrystal = add(new BoolSetting("Anti crystal", "Break crystals sitting where a block should go", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face the placement server-side only", true));
    private final EnumSetting<Interact.Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects the block, Silent switches back in the same tick", Interact.Switch.SILENT));

    private final Map<Integer, Long> hit = new ConcurrentHashMap<>();
    private BlockPos start;
    private double startY;
    private int centerTicks;

    public Surround() {
        super("Surround", "Surrounds your feet with obsidian", Category.COMBAT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Override
    protected void onEnable() {
        start = null;
        centerTicks = 0;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null) return;
        long now = System.currentTimeMillis();
        hit.values().removeIf(t -> now - t > REHIT_MS);

        if (mode.get() == Mode.STAY) {
            if (start == null) {
                start = mc.player.blockPosition();
                startY = mc.player.getY();
            } else if (mc.player.getBlockX() != start.getX() || mc.player.getBlockZ() != start.getZ() || mc.player.getY() > startY + JUMP_TOLERANCE) {
                setEnabled(false);
                return;
            }
        } else if (!mc.player.onGround() || mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
            return;
        }

        Predicate<ItemStack> block = BLOCKS.stream().filter(p -> Interact.handFor(p, switchMode.get()) != null).findFirst().orElse(null);
        if (block == null) return;

        List<BlockHitResult> hits = new ArrayList<>();
        List<EndCrystal> crystals = new ArrayList<>();
        Set<BlockPos> needed = needed();
        for (BlockPos pos : needed) {
            if (hits.size() >= blocksPerTick.getInt()) break;
            List<Entity> blocking = mc.level.getEntities((Entity) null, new AABB(pos), en -> en.blocksBuilding);
            if (!blocking.isEmpty()) {
                if (!antiCrystal.get() || !blocking.stream().allMatch(en -> en instanceof EndCrystal)) continue;
                blocking.forEach(en -> crystals.add((EndCrystal) en));
            }
            BlockHitResult against = Interact.placeAgainst(pos, REACH, REACH);
            if (against == null && !needed.contains(pos.below())) against = helperBelow(pos);
            if (against != null) hits.add(against);
        }
        if (hits.isEmpty()) return;

        Runnable act = () -> {
            for (EndCrystal c : crystals) {
                if (hit.containsKey(c.getId())) continue;
                mc.getConnection().send(Packets.attack(c.getId(), mc.player.isShiftKeyDown()));
                mc.player.swing(InteractionHand.MAIN_HAND);
                hit.put(c.getId(), System.currentTimeMillis());
            }
            Interact.useOn(block, hits, switchMode.get());
        };
        if (rotate.get()) Rotations.rotate(hits.getFirst().getLocation(), ROTATION_PRIORITY, act);
        else act.run();
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        if (mode.get() != Mode.STAY || !center.get() || start == null || centerTicks >= CENTER_TICKS) return;
        centerTicks++;
        Vec3 to = new Vec3(start.getX() + 0.5 - mc.player.getX(), 0, start.getZ() + 0.5 - mc.player.getZ());
        double dist = to.length();
        if (dist < CENTERED) {
            centerTicks = CENTER_TICKS;
            return;
        }
        Vec3 step = to.scale(Math.min(CENTER_SPEED, dist) / dist);
        e.movement = new Vec3(step.x, e.movement.y, step.z);
    }

    /** Floor first, then the feet ring, then the head ring on top of it. Only spots that still need a block. */
    private Set<BlockPos> needed() {
        AABB box = mc.player.getBoundingBox();
        int y = (int) Math.floor(mc.player.getY() + 0.01);
        Set<BlockPos> feet = new LinkedHashSet<>();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX - 1e-6); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ - 1e-6); z++) feet.add(new BlockPos(x, y, z));
        }

        Set<BlockPos> needed = new LinkedHashSet<>();
        if (floor.get()) feet.forEach(f -> needed.add(f.below()));
        for (int level = 0; level < height.getInt(); level++) {
            for (BlockPos f : feet) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = f.relative(dir);
                    if (!feet.contains(side)) needed.add(side.above(level));
                }
            }
        }
        needed.removeIf(p -> !mc.level.getBlockState(p).canBeReplaced());
        return needed;
    }

    /** When a feet-level spot floats over air, place the block under it first. Head-level spots wait for the feet ring. */
    private BlockHitResult helperBelow(BlockPos pos) {
        BlockPos below = pos.below();
        if (!mc.level.getBlockState(below).canBeReplaced()) return null;
        return Interact.placeAgainst(below, REACH, REACH);
    }
}
