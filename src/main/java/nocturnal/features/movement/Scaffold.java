package nocturnal.features.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
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
import nocturnal.util.Rotations;

import java.util.List;
import java.util.function.Predicate;

/** SafeWalk stops you at edges. Bridge places blocks under you as you walk, and Tower pillars up while you hold jump. */
public class Scaffold extends Module {
    public enum Mode { SAFEWALK, BRIDGE }

    private static final int ROTATION_PRIORITY = 30;
    private static final double REACH = 4.5;
    private static final double JUMP_VELOCITY = 0.42;

    /** Plain full blocks only: no containers, nothing that falls, nothing you'd walk through. */
    private static final Predicate<ItemStack> BLOCK = s -> s.getItem() instanceof BlockItem item && isScaffoldBlock(item.getBlock());

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "SafeWalk only stops you at edges, Bridge places blocks under you", Mode.BRIDGE));
    private final BoolSetting tower = add(new BoolSetting("Tower", "Bridge: hold jump without moving to pillar straight up", true));
    private final NumberSetting extend = add(new NumberSetting("Extend", "Bridge: place this many ticks ahead of where you're walking", 1, 0, 3, 1));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face the placement server-side only", true));
    private final EnumSetting<Interact.Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects the block, Silent switches back in the same tick", Interact.Switch.SILENT));

    public Scaffold() {
        super("Scaffold", "Stops you at edges or bridges under you", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Subscribe
    private void onEdge(Events.StopAtEdge e) {
        if (mode.get() == Mode.SAFEWALK || Interact.handFor(BLOCK, switchMode.get()) == null) e.cancel();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null || mode.get() != Mode.BRIDGE) return;
        if (Interact.handFor(BLOCK, switchMode.get()) == null) return;

        Vec3 ahead = mc.player.position().add(mc.player.getDeltaMovement().multiply(extend.get(), 0, extend.get()));
        BlockPos below = BlockPos.containing(ahead.x, Math.floor(mc.player.getY()) - 1, ahead.z);
        if (!mc.level.getBlockState(below).canBeReplaced()) {
            below = mc.player.blockPosition().below();
            if (!mc.level.getBlockState(below).canBeReplaced()) return;
        }

        BlockPos target = below;
        BlockHitResult hit = Interact.placeAgainst(below, REACH, REACH);
        if (hit == null) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos helper = below.relative(dir);
                if (!mc.level.getBlockState(helper).canBeReplaced()) continue;
                hit = Interact.placeAgainst(helper, REACH, REACH);
                if (hit != null) {
                    target = helper;
                    break;
                }
            }
        }
        if (hit == null) return;

        boolean towering = tower.get() && mc.options.keyJump.isDown() && mc.player.input.getMoveVector().lengthSquared() == 0
            && target.equals(mc.player.blockPosition().below());
        BlockHitResult place = hit;
        Runnable act = () -> {
            if (Interact.useOn(BLOCK, List.of(place), switchMode.get()) && towering && !mc.player.onGround()) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, JUMP_VELOCITY, v.z);
            }
        };
        if (rotate.get()) Rotations.rotate(hit.getLocation(), ROTATION_PRIORITY, act);
        else act.run();
    }

    private static boolean isScaffoldBlock(Block block) {
        if (block instanceof EntityBlock || block instanceof FallingBlock) return false;
        var state = block.defaultBlockState();
        return state.isSolid() && state.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO);
    }
}
