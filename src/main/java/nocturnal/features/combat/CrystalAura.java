package nocturnal.features.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.features.render.ClickGui;
import nocturnal.mixin.MultiPlayerGameModeAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import nocturnal.util.Packets;
import nocturnal.util.Rotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CrystalAura extends Module {
    public enum Switch { NONE, NORMAL, SILENT }

    private static final int ROTATION_PRIORITY = 20;
    /** How long to wait for the server to remove a crystal we hit before hitting it again. */
    private static final long REHIT_MS = 300;
    /** How long a placement is remembered so Predict can recognize the crystal when it spawns. */
    private static final long PLACED_MS = 1000;
    private static final int REJECTED_COLOR = 0xFFFF5555;

    private final NumberSetting targetRange = add(new NumberSetting("Target range", "How far to look for players", 10, 3, 16, 0.5));
    private final BoolSetting place = add(new BoolSetting("Place", "Place crystals", true));
    private final NumberSetting placeRange = add(new NumberSetting("Place range", "Reach for placing", 4.5, 1, 6, 0.1));
    private final NumberSetting placeDelay = add(new NumberSetting("Place delay", "Ticks between placements", 0, 0, 10, 1));
    private final NumberSetting multiPlace = add(new NumberSetting("Multi place", "Crystals placed per tick", 1, 1, 3, 1));
    private final BoolSetting breakCrystals = add(new BoolSetting("Break", "Break crystals", true));
    private final NumberSetting breakRange = add(new NumberSetting("Break range", "Reach for breaking", 4.5, 1, 6, 0.1));
    private final NumberSetting breakDelay = add(new NumberSetting("Break delay", "Ticks between breaks", 0, 0, 10, 1));
    private final NumberSetting wallsRange = add(new NumberSetting("Walls range", "Reach for spots and crystals you can't see", 3, 0, 6, 0.1));
    private final BoolSetting predict = add(new BoolSetting("Predict", "Break our crystals the moment the server spawns them", true));
    private final NumberSetting minDamage = add(new NumberSetting("Min damage", "Least damage to a target worth acting on (lethal hits always count)", 6, 0, 36, 0.5));
    private final NumberSetting maxSelf = add(new NumberSetting("Max self", "Most damage we're willing to take per crystal", 12, 0, 36, 0.5));
    private final BoolSetting antiSuicide = add(new BoolSetting("Anti suicide", "Never take a hit that would kill us", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face what we place or break, server-side only", true));
    private final EnumSetting<Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects crystals, Silent switches back in the same tick", Switch.SILENT));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause on use", "Don't act while eating or drinking", true));
    private final BoolSetting render = add(new BoolSetting("Render", "Show the chosen spot with target / self damage, or the best rejected spot in red with the reason", true));

    /** Crystal entity id to when we hit it. Written from the network thread too. */
    private final Map<Integer, Long> hit = new ConcurrentHashMap<>();
    /** Base block of crystals we placed to when we placed them. Read from the network thread. */
    private final Map<BlockPos, Long> placed = new ConcurrentHashMap<>();
    private List<Player> targets = List.of();
    private int placeTimer, breakTimer;

    /** Damage an explosion would do to the best target and to us, and why it was rejected (null if accepted). */
    private record Eval(float target, float self, String reject) {}

    private BlockPos previewPos;
    private Eval preview;

    public CrystalAura() {
        super("CrystalAura", "Places and breaks end crystals at nearby players", Category.COMBAT);
    }

    @Override
    public String info() {
        return targets.isEmpty() ? null : targets.getFirst().getName().getString();
    }

    @Override
    protected void onDisable() {
        hit.clear();
        placed.clear();
        targets = List.of();
        previewPos = null;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        long now = System.currentTimeMillis();
        hit.values().removeIf(t -> now - t > REHIT_MS);
        placed.values().removeIf(t -> now - t > PLACED_MS);
        placeTimer++;
        breakTimer++;
        targets = List.of();
        previewPos = null;
        if (!inGame() || mc.gameMode == null || (pauseOnUse.get() && mc.player.isUsingItem())) return;

        targets = findTargets();
        if (targets.isEmpty()) return;

        EndCrystal crystal = breakCrystals.get() && breakTimer > breakDelay.getInt() ? bestCrystal() : null;
        List<BlockPos> spots = place.get() && crystalHand() != null ? bestSpots(crystal) : List.of();
        if (placeTimer <= placeDelay.getInt()) spots = List.of();
        if (crystal == null && spots.isEmpty()) return;

        List<BlockPos> toPlace = spots;
        Runnable act = () -> {
            if (crystal != null) breakCrystal(crystal);
            placeAll(toPlace);
        };
        if (!rotate.get()) {
            act.run();
            return;
        }
        Vec3 aim = crystal != null ? Combat.aimPoint(crystal.getBoundingBox()) : topCenter(spots.getFirst());
        Rotations.rotate(aim, ROTATION_PRIORITY, act);
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (!predict.get() || !breakCrystals.get() || !(e.packet instanceof ClientboundAddEntityPacket p)) return;
        if (p.getType() != EntityType.END_CRYSTAL || !placed.containsKey(BlockPos.containing(p.getX(), p.getY() - 1, p.getZ()))) return;
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().send(Packets.attack(p.getId(), mc.player.isShiftKeyDown()));
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        hit.put(p.getId(), System.currentTimeMillis());
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (!render.get() || previewPos == null) return;
        Eval eval = preview;
        int color = eval.reject == null ? ClickGui.accent() : REJECTED_COLOR;
        Gizmos.cuboid(new AABB(previewPos), GizmoStyle.stroke(color, 2)).setAlwaysOnTop();
        String text = String.format("%.1f / %.1f", eval.target, eval.self) + (eval.reject == null ? "" : " " + eval.reject);
        Gizmos.billboardText(text, topCenter(previewPos).add(0, 0.6, 0), TextGizmo.Style.forColorAndCentered(color)).setAlwaysOnTop();
    }

    private List<Player> findTargets() {
        double r = targetRange.get();
        return mc.level.players().stream()
            .filter(p -> Combat.isValid(p) && p.distanceTo(mc.player) <= r)
            .sorted(Comparator.comparingDouble(p -> p.distanceTo(mc.player)))
            .map(p -> (Player) p)
            .toList();
    }

    private EndCrystal bestCrystal() {
        double range = breakRange.get();
        EndCrystal best = null;
        float bestDamage = 0;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range + 2), e -> e instanceof EndCrystal)) {
            double reach = Combat.reach(entity.getBoundingBox());
            if (hit.containsKey(entity.getId()) || reach > range) continue;
            if (reach > wallsRange.get() && !canSee(entity.getBoundingBox().getCenter(), null)) continue;
            Eval eval = evaluate(entity.position());
            if (eval.reject == null && eval.target > bestDamage) {
                bestDamage = eval.target;
                best = (EndCrystal) entity;
            }
        }
        return best;
    }

    /** Best spots to place this tick, spaced so the crystals don't block each other. Also picks the preview. */
    private List<BlockPos> bestSpots(EndCrystal breaking) {
        double range = placeRange.get();
        int r = (int) Math.ceil(range);
        BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
        Vec3 eyePos = mc.player.getEyePosition();

        record Spot(BlockPos pos, Eval eval) {}
        List<Spot> accepted = new ArrayList<>();
        Spot bestRejected = null;
        for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-r, -r, -r), eye.offset(r, r, r))) {
            Vec3 top = topCenter(pos);
            double reach = top.distanceTo(eyePos);
            if (reach > range || !canPlace(pos, breaking)) continue;
            if (reach > wallsRange.get() && !canSee(top, pos)) continue;
            Eval eval = evaluate(top);
            Spot spot = new Spot(pos.immutable(), eval);
            if (eval.reject == null) accepted.add(spot);
            else if (eval.target > 0 && (bestRejected == null || eval.target > bestRejected.eval.target)) bestRejected = spot;
        }
        accepted.sort(Comparator.comparingDouble(s -> -s.eval.target));

        Spot shown = accepted.isEmpty() ? bestRejected : accepted.getFirst();
        if (shown != null) {
            previewPos = shown.pos;
            preview = shown.eval;
        }

        List<BlockPos> chosen = new ArrayList<>();
        for (Spot s : accepted) {
            if (chosen.size() >= multiPlace.getInt()) break;
            if (chosen.stream().allMatch(c -> c.distChessboard(s.pos) >= 2)) chosen.add(s.pos);
        }
        return chosen;
    }

    private boolean canPlace(BlockPos pos, EndCrystal breaking) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) return false;
        BlockPos above = pos.above();
        if (!mc.level.isEmptyBlock(above)) return false;
        AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);
        return mc.level.getEntities((Entity) null, space, e -> e != breaking && !hit.containsKey(e.getId())).isEmpty();
    }

    /** True if nothing blocks our eyes from the point. When aiming at a block's face, hitting that block counts. */
    private boolean canSee(Vec3 point, BlockPos block) {
        BlockHitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS || (block != null && result.getBlockPos().equals(block));
    }

    private Eval evaluate(Vec3 center) {
        float self = Combat.crystalDamage(mc.player, center);
        float best = 0;
        boolean lethal = false;
        for (Player target : targets) {
            float damage = Combat.crystalDamage(target, center);
            if (damage > best) {
                best = damage;
                lethal = damage >= Combat.health(target);
            }
        }
        String reject = antiSuicide.get() && self >= Combat.health(mc.player) ? "would kill you"
            : self > maxSelf.get() ? "self > max"
            : best < minDamage.get() && !lethal ? "below min"
            : null;
        return new Eval(best, self, reject);
    }

    private void breakCrystal(EndCrystal crystal) {
        if (crystal.isRemoved()) return;
        mc.getConnection().send(Packets.attack(crystal.getId(), mc.player.isShiftKeyDown()));
        mc.player.swing(InteractionHand.MAIN_HAND);
        hit.put(crystal.getId(), System.currentTimeMillis());
        breakTimer = 0;
    }

    /** Places through the normal game path so the server, item count and slot stay in sync, switching once for all spots. */
    private void placeAll(List<BlockPos> spots) {
        if (spots.isEmpty()) return;
        InteractionHand hand = crystalHand();
        if (hand == null) return;

        Inventory inventory = mc.player.getInventory();
        int restore = -1;
        if (hand == InteractionHand.MAIN_HAND && !mc.player.getMainHandItem().is(Items.END_CRYSTAL)) {
            if (switchMode.get() == Switch.SILENT) restore = inventory.getSelectedSlot();
            inventory.setSelectedSlot(hotbarCrystal());
        }
        for (BlockPos pos : spots) {
            mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(topCenter(pos), Direction.UP, pos, false));
            mc.player.swing(hand);
            placed.put(pos, System.currentTimeMillis());
        }
        placeTimer = 0;
        if (restore >= 0) {
            inventory.setSelectedSlot(restore);
            ((MultiPlayerGameModeAccessor) mc.gameMode).nocturnal$syncSelectedSlot();
        }
    }

    /** The hand to place with, or null if we can't reach a crystal with the current switch mode. */
    private InteractionHand crystalHand() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return InteractionHand.OFF_HAND;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return InteractionHand.MAIN_HAND;
        return switchMode.get() != Switch.NONE && hotbarCrystal() >= 0 ? InteractionHand.MAIN_HAND : null;
    }

    private int hotbarCrystal() {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }

    private static Vec3 topCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    }
}
