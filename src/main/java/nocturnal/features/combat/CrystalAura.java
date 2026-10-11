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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.features.render.ClickGui;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import nocturnal.util.Interact;
import nocturnal.util.Packets;
import nocturnal.util.Rotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class CrystalAura extends Module {
    private static final Predicate<ItemStack> CRYSTAL = s -> s.is(Items.END_CRYSTAL);
    private static final Predicate<ItemStack> OBSIDIAN = s -> s.is(Items.OBSIDIAN);

    private static final int ROTATION_PRIORITY = 20;
    /** How long to wait for the server to remove a crystal we hit before hitting it again. */
    private static final long REHIT_MS = 300;
    /** How long a placement is remembered so Predict can recognize the crystal when it spawns. */
    private static final long PLACED_MS = 1000;
    /** Ticks between obsidian placements, so the last one lands before we judge spots again. */
    private static final int SUPPORT_TICKS = 3;
    /** Auto position: how often to look for a better place to stand, how far, and how much better it must be. */
    private static final int REPOSITION_TICKS = 10, REPOSITION_RADIUS = 2, REPOSITION_GIVE_UP_TICKS = 40;
    private static final float REPOSITION_MIN_GAIN = 2;
    /** Auto position only judges this many of the best spots, to keep the cost down. */
    private static final int REPOSITION_SPOTS = 6;
    private static final double WALK_SPEED = 0.2158, ARRIVED = 0.1;
    private static final int REJECTED_COLOR = 0xFFFF5555, SUPPORT_COLOR = 0xFFFFAA00, GOAL_COLOR = 0xFF55FFFF;

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
    private final BoolSetting cover = add(new BoolSetting("Cover", "Prefer spots where blocks shield you: ranks by target damage minus your damage", true));
    private final BoolSetting support = add(new BoolSetting("Support", "Place obsidian to make a spot when there's none good", true));
    private final BoolSetting autoPosition = add(new BoolSetting("Auto position", "Walk up to 2 blocks to where you deal more and take less. Takes over movement while walking", false));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face what we place or break, server-side only", true));
    private final EnumSetting<Interact.Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects the item, Silent switches back in the same tick", Interact.Switch.SILENT));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause on use", "Don't act while eating or drinking", true));
    private final BoolSetting render = add(new BoolSetting("Render", "Show the chosen spot with target / self damage, or the best rejected spot in red with the reason", true));

    /** Crystal entity id to when we hit it. Written from the network thread too. */
    private final Map<Integer, Long> hit = new ConcurrentHashMap<>();
    /** Base block of crystals we placed to when we placed them. Read from the network thread. */
    private final Map<BlockPos, Long> placed = new ConcurrentHashMap<>();
    private List<Player> targets = List.of();
    private int placeTimer, breakTimer, supportTimer, repositionTimer, goalAge;

    /** Damage an explosion would do to the best target and to us, and why it was rejected (null if accepted). */
    private record Eval(float target, float self, String reject) {}
    private record Spot(BlockPos pos, Eval eval) {}

    private Spot preview;
    private BlockPos supportPreview;
    private Vec3 goal;

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
        preview = null;
        supportPreview = null;
        goal = null;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        long now = System.currentTimeMillis();
        hit.values().removeIf(t -> now - t > REHIT_MS);
        placed.values().removeIf(t -> now - t > PLACED_MS);
        placeTimer++;
        breakTimer++;
        supportTimer++;
        targets = List.of();
        preview = null;
        supportPreview = null;
        if (!inGame() || mc.gameMode == null || (pauseOnUse.get() && mc.player.isUsingItem())) {
            goal = null;
            return;
        }

        targets = findTargets();
        if (targets.isEmpty()) {
            goal = null;
            return;
        }
        if (autoPosition.get()) updateGoal();

        EndCrystal crystal = breakCrystals.get() && breakTimer > breakDelay.getInt() ? bestCrystal() : null;
        boolean canPlace = place.get() && placeTimer > placeDelay.getInt() && canReach(CRYSTAL);
        List<BlockPos> spots = canPlace ? bestSpots(crystal) : List.of();
        BlockHitResult supportHit = canPlace && spots.isEmpty() && crystal == null ? supportHit() : null;
        if (crystal == null && spots.isEmpty() && supportHit == null) return;

        List<BlockPos> toPlace = spots;
        Runnable act = () -> {
            if (crystal != null) breakCrystal(crystal);
            if (!toPlace.isEmpty()) {
                Interact.useOn(CRYSTAL, toPlace.stream().map(p -> new BlockHitResult(topCenter(p), Direction.UP, p, false)).toList(), switchMode.get());
                toPlace.forEach(p -> placed.put(p, System.currentTimeMillis()));
                placeTimer = 0;
            } else if (supportHit != null) {
                Interact.useOn(OBSIDIAN, List.of(supportHit), switchMode.get());
                supportTimer = 0;
            }
        };
        if (!rotate.get()) {
            act.run();
            return;
        }
        Vec3 aim = crystal != null ? Combat.aimPoint(crystal.getBoundingBox())
            : !spots.isEmpty() ? topCenter(spots.getFirst())
            : supportHit.getLocation();
        Rotations.rotate(aim, ROTATION_PRIORITY, act);
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        if (goal == null || !autoPosition.get() || !mc.player.onGround()) return;
        Vec3 to = new Vec3(goal.x - mc.player.getX(), 0, goal.z - mc.player.getZ());
        double dist = to.length();
        if (dist < ARRIVED || ++goalAge > REPOSITION_GIVE_UP_TICKS) {
            goal = null;
            return;
        }
        Vec3 step = to.scale(Math.min(WALK_SPEED, dist) / dist);
        e.movement = new Vec3(step.x, e.movement.y, step.z);
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
        if (!render.get()) return;
        if (preview != null) {
            Eval eval = preview.eval;
            int color = eval.reject == null ? ClickGui.accent() : REJECTED_COLOR;
            Gizmos.cuboid(new AABB(preview.pos), GizmoStyle.stroke(color, 2)).setAlwaysOnTop();
            String text = String.format("%.1f / %.1f", eval.target, eval.self) + (eval.reject == null ? "" : " " + eval.reject);
            Gizmos.billboardText(text, topCenter(preview.pos).add(0, 0.6, 0), TextGizmo.Style.forColorAndCentered(color)).setAlwaysOnTop();
        }
        if (supportPreview != null) Gizmos.cuboid(new AABB(supportPreview), GizmoStyle.stroke(SUPPORT_COLOR, 2)).setAlwaysOnTop();
        if (goal != null) {
            Gizmos.cuboid(new AABB(goal.x - 0.3, goal.y, goal.z - 0.3, goal.x + 0.3, goal.y + 0.1, goal.z + 0.3), GizmoStyle.stroke(GOAL_COLOR, 2)).setAlwaysOnTop();
        }
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
        float bestScore = Float.NEGATIVE_INFINITY;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range + 2), e -> e instanceof EndCrystal)) {
            double reach = Combat.reach(entity.getBoundingBox());
            if (hit.containsKey(entity.getId()) || reach > range) continue;
            if (reach > wallsRange.get() && !Interact.canSee(entity.getBoundingBox().getCenter(), null)) continue;
            Eval eval = evaluate(entity.position());
            if (eval.reject == null && score(eval) > bestScore) {
                bestScore = score(eval);
                best = (EndCrystal) entity;
            }
        }
        return best;
    }

    /** Best spots to place this tick, spaced so the crystals don't block each other. Also picks the preview. */
    private List<BlockPos> bestSpots(EndCrystal breaking) {
        List<Spot> accepted = new ArrayList<>();
        Spot bestRejected = null;
        for (BlockPos pos : reachableBases(mc.player.getEyePosition(), placeRange.get(), breaking)) {
            Spot spot = new Spot(pos, evaluate(topCenter(pos)));
            if (spot.eval.reject == null) accepted.add(spot);
            else if (spot.eval.target > 0 && (bestRejected == null || spot.eval.target > bestRejected.eval.target)) bestRejected = spot;
        }
        accepted.sort(Comparator.comparingDouble(s -> -score(s.eval)));
        preview = accepted.isEmpty() ? bestRejected : accepted.getFirst();

        List<BlockPos> chosen = new ArrayList<>();
        for (Spot s : accepted) {
            if (chosen.size() >= multiPlace.getInt()) break;
            if (chosen.stream().allMatch(c -> c.distChessboard(s.pos) >= 2)) chosen.add(s.pos);
        }
        return chosen;
    }

    /** Obsidian and bedrock within range of eye that a crystal can go on, respecting the walls range. */
    private List<BlockPos> reachableBases(Vec3 eye, double range, EndCrystal breaking) {
        int r = (int) Math.ceil(range);
        BlockPos center = BlockPos.containing(eye);
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            Vec3 top = topCenter(pos);
            double reach = top.distanceTo(eye);
            if (reach > range || !isBase(mc.level.getBlockState(pos)) || !hasCrystalSpace(pos, breaking)) continue;
            if (reach > wallsRange.get() && !Interact.canSee(top, pos)) continue;
            found.add(pos.immutable());
        }
        return found;
    }

    private static boolean isBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    /** Air above with nothing standing in the way of a crystal. */
    private boolean hasCrystalSpace(BlockPos pos, EndCrystal breaking) {
        BlockPos above = pos.above();
        if (!mc.level.isEmptyBlock(above)) return false;
        AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);
        return mc.level.getEntities((Entity) null, space, e -> e != breaking && !hit.containsKey(e.getId())).isEmpty();
    }

    private Eval evaluate(Vec3 center) {
        return evaluate(center, bestTargetDamage(center), Combat.crystalDamage(mc.player, center));
    }

    private Eval evaluate(Vec3 center, float[] target, float self) {
        String reject = antiSuicide.get() && self >= Combat.health(mc.player) ? "would kill you"
            : self > maxSelf.get() ? "self > max"
            : target[0] < minDamage.get() && target[1] == 0 ? "below min"
            : null;
        return new Eval(target[0], self, reject);
    }

    /** Highest damage to any target, and 1 if that hit is lethal. */
    private float[] bestTargetDamage(Vec3 center) {
        float best = 0, lethal = 0;
        for (Player target : targets) {
            float damage = Combat.crystalDamage(target, center);
            if (damage > best) {
                best = damage;
                lethal = damage >= Combat.health(target) ? 1 : 0;
            }
        }
        return new float[]{best, lethal};
    }

    private float score(Eval eval) {
        return cover.get() ? eval.target - eval.self : eval.target;
    }

    private void breakCrystal(EndCrystal crystal) {
        if (crystal.isRemoved()) return;
        mc.getConnection().send(Packets.attack(crystal.getId(), mc.player.isShiftKeyDown()));
        mc.player.swing(InteractionHand.MAIN_HAND);
        hit.put(crystal.getId(), System.currentTimeMillis());
        breakTimer = 0;
    }

    /** Where to put obsidian so a crystal on it hits the closest target well, or null. */
    private BlockHitResult supportHit() {
        if (!support.get() || supportTimer <= SUPPORT_TICKS || !canReach(OBSIDIAN) || !canReach(CRYSTAL)) return null;
        Player target = targets.getFirst();
        BlockPos feet = target.blockPosition();
        Vec3 eye = mc.player.getEyePosition();
        BlockHitResult best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 0, 2))) {
            if (topCenter(pos).distanceTo(eye) > placeRange.get() || !canHoldObsidian(pos)) continue;
            BlockHitResult against = Interact.placeAgainst(pos, placeRange.get(), wallsRange.get());
            if (against == null) continue;
            Eval eval = evaluate(topCenter(pos));
            if (eval.reject == null && score(eval) > bestScore) {
                bestScore = score(eval);
                best = against;
                supportPreview = pos.immutable();
            }
        }
        return best;
    }

    private boolean canHoldObsidian(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced()
            && mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())
            && hasCrystalSpace(pos, null);
    }

    /** Every few ticks, looks for a safe spot within 2 blocks where our best crystal scores clearly higher. */
    private void updateGoal() {
        if (goal != null || repositionTimer++ < REPOSITION_TICKS || !mc.player.onGround()) return;
        repositionTimer = 0;

        Vec3 eye = mc.player.getEyePosition();
        double poolRange = placeRange.get() + REPOSITION_RADIUS + 1;
        record Pooled(BlockPos pos, Vec3 center, float[] target) {}
        List<Pooled> pool = new ArrayList<>();
        for (BlockPos pos : reachableBases(eye, poolRange, null)) {
            Vec3 center = topCenter(pos);
            float[] target = bestTargetDamage(center);
            if (target[0] >= minDamage.get() || target[1] == 1) pool.add(new Pooled(pos, center, target));
        }
        if (pool.isEmpty()) return;
        pool.sort(Comparator.comparingDouble(p -> -p.target[0]));
        List<Pooled> top = pool.subList(0, Math.min(REPOSITION_SPOTS, pool.size()));

        float here = Float.NEGATIVE_INFINITY;
        for (Pooled p : top) here = Math.max(here, scoreFrom(mc.player.position(), p.center, p.target));

        BlockPos standing = mc.player.blockPosition();
        Vec3 bestGoal = null;
        float bestScore = here + REPOSITION_MIN_GAIN;
        for (BlockPos s : BlockPos.betweenClosed(standing.offset(-REPOSITION_RADIUS, -1, -REPOSITION_RADIUS), standing.offset(REPOSITION_RADIUS, 1, REPOSITION_RADIUS))) {
            Vec3 feet = Vec3.atBottomCenterOf(s);
            if (!isSafeToStand(s) || !isSafePath(feet)) continue;
            float score = Float.NEGATIVE_INFINITY;
            for (Pooled p : top) score = Math.max(score, scoreFrom(feet, p.center, p.target));
            if (score > bestScore) {
                bestScore = score;
                bestGoal = feet;
            }
        }
        goal = bestGoal;
        goalAge = 0;
    }

    /** Score of a crystal at center if we stood at feet, or negative infinity if unreachable or unsafe there. */
    private float scoreFrom(Vec3 feet, Vec3 center, float[] target) {
        if (feet.add(0, mc.player.getEyeHeight(), 0).distanceTo(center) > placeRange.get()) return Float.NEGATIVE_INFINITY;
        AABB box = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(feet);
        Eval eval = evaluate(center, target, Combat.crystalDamage(mc.player, center, box));
        return eval.reject == null ? score(eval) : Float.NEGATIVE_INFINITY;
    }

    private boolean isSafeToStand(BlockPos s) {
        BlockPos below = s.below();
        if (!mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)) return false;
        for (BlockPos p : List.of(s, s.above())) {
            BlockState state = mc.level.getBlockState(p);
            if (!state.getCollisionShape(mc.level, p).isEmpty() || !state.getFluidState().isEmpty() || isHazard(state)) return false;
        }
        if (isHazard(mc.level.getBlockState(below))) return false;
        AABB box = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(s));
        for (Entity e : mc.level.getEntities(mc.player, box.inflate(12), e -> e instanceof EndCrystal)) {
            if (Combat.crystalDamage(mc.player, e.position(), box) >= Combat.health(mc.player)) return false;
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }

    /** No drop of more than one block anywhere on the straight walk to feet. */
    private boolean isSafePath(Vec3 feet) {
        Vec3 from = mc.player.position();
        int steps = (int) Math.ceil(from.distanceTo(feet) / 0.25);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(feet, (double) i / steps);
            BlockPos floor = BlockPos.containing(p.x, p.y - 0.5, p.z);
            boolean ground = !mc.level.getBlockState(floor).getCollisionShape(mc.level, floor).isEmpty()
                || !mc.level.getBlockState(floor.below()).getCollisionShape(mc.level, floor.below()).isEmpty();
            if (!ground || isHazard(mc.level.getBlockState(floor)) || isHazard(mc.level.getBlockState(floor.above()))) return false;
        }
        return true;
    }

    private boolean canReach(Predicate<ItemStack> item) {
        return Interact.handFor(item, switchMode.get()) != null;
    }

    private static Vec3 topCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    }
}
