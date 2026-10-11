package nocturnal.features.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.features.misc.AutoTool;
import nocturnal.mixin.MultiPlayerGameModeAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.ListSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Rotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Breaks blocks around you. Anything that breaks in one tick is broken several at a time; harder blocks are mined one
 * by one with normal progress.
 */
public class Nuker extends Module {
    public enum Shape { SPHERE, CUBE }
    public enum Filter { ALL, WHITELIST, BLACKLIST }

    private static final int ROTATION_PRIORITY = 5;

    private final NumberSetting range = add(new NumberSetting("Range", "How far from your eyes to break", 4.5, 1, 6, 0.5));
    private final EnumSetting<Shape> shape = add(new EnumSetting<>("Shape", "Sphere or cube around you", Shape.SPHERE));
    private final EnumSetting<Filter> filter = add(new EnumSetting<>("Filter", "All blocks, only the whitelist, or all but the blacklist", Filter.ALL));
    private final ListSetting<Block> whitelist = add(new ListSetting<>("Whitelist", "Blocks to break in Whitelist mode", BuiltInRegistries.BLOCK, Blocks.NETHERRACK));
    private final ListSetting<Block> blacklist = add(new ListSetting<>("Blacklist", "Blocks to skip in Blacklist mode", BuiltInRegistries.BLOCK,
        Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.BARREL, Blocks.SPAWNER));
    private final BoolSetting flatten = add(new BoolSetting("Flatten", "Never break blocks below your feet", false));
    private final NumberSetting instantPerTick = add(new NumberSetting("Instant per tick", "Most one-tick blocks broken each tick", 6, 1, 20, 1));
    private final BoolSetting instantOnly = add(new BoolSetting("Instant only", "Skip blocks that take more than one tick", false));
    private final BoolSetting autoTool = add(new BoolSetting("Auto tool", "Switch to the best hotbar tool for each block", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face each block server-side only", true));

    private BlockPos mining;

    public Nuker() {
        super("Nuker", "Breaks blocks around you", Category.WORLD);
    }

    @Override
    protected void onDisable() {
        if (mining != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        mining = null;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null || mc.screen != null) return;
        List<BlockPos> targets = targets();
        if (targets.isEmpty()) {
            if (mining != null) mc.gameMode.stopDestroyBlock();
            mining = null;
            return;
        }

        BlockPos first = targets.getFirst();
        if (autoTool.get()) {
            int tool = AutoTool.bestTool(mc.level.getBlockState(first), false);
            if (tool >= 0) mc.player.getInventory().setSelectedSlot(tool);
        }

        List<BlockPos> instant = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (instant.size() >= instantPerTick.getInt()) break;
            if (mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos) >= 1) instant.add(pos);
        }

        Runnable act;
        BlockPos look;
        if (!instant.isEmpty()) {
            look = instant.getFirst();
            act = () -> {
                ((MultiPlayerGameModeAccessor) mc.gameMode).nocturnal$syncSelectedSlot();
                for (BlockPos pos : instant) {
                    mc.gameMode.startDestroyBlock(pos, face(pos));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            };
        } else if (!instantOnly.get()) {
            if (mining == null || !targets.contains(mining)) mining = first;
            look = mining;
            BlockPos pos = mining;
            act = () -> {
                mc.gameMode.continueDestroyBlock(pos, face(pos));
                mc.player.swing(InteractionHand.MAIN_HAND);
            };
        } else {
            return;
        }
        if (rotate.get()) Rotations.rotate(Vec3.atCenterOf(look), ROTATION_PRIORITY, act);
        else act.run();
    }

    /** Breakable blocks in range that pass the filter, nearest first. */
    private List<BlockPos> targets() {
        Vec3 eye = mc.player.getEyePosition();
        double r = range.get();
        int reach = (int) Math.ceil(r);
        BlockPos center = BlockPos.containing(eye);
        int feetY = mc.player.getBlockY();
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (flatten.get() && pos.getY() < feetY) continue;
            if (shape.get() == Shape.SPHERE && Vec3.atCenterOf(pos).distanceToSqr(eye) > r * r) continue;
            if (shape.get() == Shape.CUBE && (Math.abs(pos.getX() + 0.5 - eye.x) > r || Math.abs(pos.getY() + 0.5 - eye.y) > r || Math.abs(pos.getZ() + 0.5 - eye.z) > r)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.liquid() || state.getDestroySpeed(mc.level, pos) < 0 || !passes(state.getBlock())) continue;
            targets.add(pos.immutable());
        }
        targets.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(eye)));
        return targets;
    }

    private boolean passes(Block block) {
        return switch (filter.get()) {
            case ALL -> true;
            case WHITELIST -> whitelist.contains(block);
            case BLACKLIST -> !blacklist.contains(block);
        };
    }

    /** The face of the block pointing most toward our eyes. */
    private Direction face(BlockPos pos) {
        return Direction.getApproximateNearest(mc.player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
    }
}
