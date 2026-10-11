package nocturnal.features.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import nocturnal.util.Interact;
import nocturnal.util.Rotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Cages the nearest enemy in 2-high walls and a roof, and rebuilds whatever they break. */
public class AutoTrap extends Module {
    private static final int ROTATION_PRIORITY = 25;
    private static final double REACH = 4.5;

    private final NumberSetting range = add(new NumberSetting("Range", "How far away a target can be", 5, 2, 7, 0.5));
    private final NumberSetting wallsRange = add(new NumberSetting("Walls range", "Reach for placements you can't see", 3, 0, 4.5, 0.5));
    private final NumberSetting blocksPerTick = add(new NumberSetting("Blocks per tick", "Most blocks placed in one tick", 4, 1, 8, 1));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face the placement server-side only", true));
    private final EnumSetting<Interact.Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects the block, Silent switches back in the same tick", Interact.Switch.SILENT));

    private Player target;

    public AutoTrap() {
        super("AutoTrap", "Cages the nearest enemy in obsidian", Category.COMBAT);
    }

    @Override
    public String info() {
        return target == null ? null : target.getName().getString();
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null) return;
        double r = range.get();
        target = mc.level.players().stream()
            .filter(p -> Combat.isValid(p) && p.distanceToSqr(mc.player) <= r * r)
            .min(Comparator.comparingDouble(p -> p.distanceToSqr(mc.player)))
            .orElse(null);
        if (target == null) return;

        Predicate<ItemStack> block = Interact.blastProofBlock(switchMode.get());
        if (block == null) return;

        List<BlockHitResult> hits = new ArrayList<>();
        for (BlockPos pos : needed(target)) {
            if (hits.size() >= blocksPerTick.getInt()) break;
            if (!mc.level.getEntities((Entity) null, new AABB(pos), en -> en.blocksBuilding).isEmpty()) continue;
            BlockHitResult against = Interact.placeAgainst(pos, REACH, wallsRange.get());
            if (against != null) hits.add(against);
        }
        if (hits.isEmpty()) return;

        Runnable act = () -> Interact.useOn(block, hits, switchMode.get());
        if (rotate.get()) Rotations.rotate(hits.getFirst().getLocation(), ROTATION_PRIORITY, act);
        else act.run();
    }

    /**
     * Bottom ring, top ring, one block above the top ring for the roof to lean on, then the roof.
     * Each layer rests on the one before, so later layers go down on the following ticks.
     */
    private Set<BlockPos> needed(Player p) {
        Set<BlockPos> feet = Interact.feet(p.getBoundingBox());
        Set<BlockPos> needed = new LinkedHashSet<>();
        for (int level = 0; level < 2; level++) {
            for (BlockPos f : feet) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = f.relative(dir);
                    if (!feet.contains(side)) needed.add(side.above(level));
                }
            }
        }
        BlockPos first = feet.iterator().next();
        needed.add(first.relative(Direction.Plane.HORIZONTAL.iterator().next()).above(2));
        feet.forEach(f -> needed.add(f.above(2)));
        needed.removeIf(pos -> !mc.level.getBlockState(pos).canBeReplaced());
        return needed;
    }
}
