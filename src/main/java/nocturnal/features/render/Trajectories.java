package nocturnal.features.render;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.NumberSetting;

import java.util.List;

/** Draws where your throw will land, and where pearls and arrows already in the air are going. */
public class Trajectories extends Module {
    private static final int MAX_STEPS = 300;
    /** Projectiles spawn this far below the thrower's eyes. */
    private static final double SPAWN_DROP = 0.1;
    private static final float AIR_INERTIA = 0.99F, THROWABLE_WATER_INERTIA = 0.8F, ARROW_WATER_INERTIA = 0.6F;
    /** Entities' hitboxes are grown by this much when projectiles test against them. */
    private static final double HIT_MARGIN = 0.3;
    private static final double MARKER = 0.15;

    /** speed and pitch offset come from each item's shootFromRotation call, gravity from the projectile class. */
    private record Throw(float speed, double gravity, float pitchOffset, boolean arrow, boolean addOwnerMotion) {}

    private static final Throw PEARL = new Throw(1.5F, 0.03, 0, false, true);
    private static final Throw POTION = new Throw(0.5F, 0.05, -20, false, true);
    private static final Throw XP_BOTTLE = new Throw(0.7F, 0.07, -20, false, true);
    private static final Throw TRIDENT = new Throw(2.5F, 0.05, 0, true, true);
    private static final Throw CROSSBOW = new Throw(3.15F, 0.05, 0, true, false);

    private final BoolSetting self = add(new BoolSetting("Self", "Path of what you're about to throw or shoot", true));
    private final BoolSetting others = add(new BoolSetting("Others", "Paths of pearls and arrows already flying", true));
    private final ColorSetting selfColor = add(new ColorSetting("Self color", "Color of your own path", 0xFFA855F7));
    private final ColorSetting pearlColor = add(new ColorSetting("Pearl color", "Color of flying pearls", 0xFF22D3EE));
    private final ColorSetting arrowColor = add(new ColorSetting("Arrow color", "Color of flying arrows and tridents", 0xFFF87171));
    private final NumberSetting width = add(new NumberSetting("Line width", "Line thickness", 2, 1, 5, 0.5));

    public Trajectories() {
        super("Trajectories", "Shows where throws and shots will land", Category.RENDER);
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (!inGame()) return;
        if (self.get()) drawSelf(e.tickDelta);
        if (!others.get()) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl) && !(entity instanceof AbstractArrow)) continue;
            Vec3 velocity = entity.getDeltaMovement();
            if (velocity.lengthSqr() < 1e-4) continue;
            Projectile p = (Projectile) entity;
            int color = p instanceof ThrownEnderpearl ? pearlColor.get() : arrowColor.get();
            Vec3 land = simulate(p.getPosition(e.tickDelta), velocity, p.getGravity(), p instanceof AbstractArrow, p.getOwner(), color);
            if (land != null && p instanceof ThrownEnderpearl && p.getOwner() != null) {
                Gizmos.billboardText(p.getOwner().getName().getString(), land.add(0, 0.5, 0), TextGizmo.Style.forColorAndCentered(color)).setAlwaysOnTop();
            }
        }
    }

    private void drawSelf(float tickDelta) {
        ItemStack stack = mc.player.getMainHandItem();
        Throw t = throwOf(stack);
        if (t == null) {
            stack = mc.player.getOffhandItem();
            t = throwOf(stack);
        }
        if (t == null) return;

        float speed = t.speed;
        if (stack.getItem() instanceof BowItem) {
            float power = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
            if (power < 0.1F) return;
            speed = power * 3;
        }

        float yaw = mc.player.getViewYRot(tickDelta), pitch = mc.player.getViewXRot(tickDelta);
        float rad = Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(
            -Mth.sin(yaw * rad) * Mth.cos(pitch * rad),
            -Mth.sin((pitch + t.pitchOffset) * rad),
            Mth.cos(yaw * rad) * Mth.cos(pitch * rad)
        ).normalize().scale(speed);
        if (t.addOwnerMotion) {
            Vec3 m = mc.player.getDeltaMovement();
            dir = dir.add(m.x, mc.player.onGround() ? 0 : m.y, m.z);
        }
        Vec3 start = mc.player.getEyePosition(tickDelta).subtract(0, SPAWN_DROP, 0);
        simulate(start, dir, t.gravity, t.arrow, mc.player, selfColor.get());
    }

    private Throw throwOf(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof EnderpearlItem || item instanceof SnowballItem || item instanceof EggItem) return PEARL;
        if (item instanceof ThrowablePotionItem) return POTION;
        if (item instanceof ExperienceBottleItem) return XP_BOTTLE;
        boolean using = mc.player.isUsingItem() && mc.player.getUseItem() == stack;
        if (item instanceof BowItem && using) return new Throw(0, 0.05, 0, true, true);
        if (item instanceof TridentItem && using) return TRIDENT;
        if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) return CROSSBOW;
        return null;
    }

    /**
     * Steps the projectile the way the server ticks it and draws the path. Throwables slow down and fall before
     * moving, arrows after. Returns where it lands, or null if it never hits anything.
     */
    private Vec3 simulate(Vec3 pos, Vec3 velocity, double gravity, boolean arrow, Entity owner, int color) {
        float w = width.get().floatValue();
        List<Entity> targets = mc.level.getEntities(owner, new AABB(pos, pos).inflate(64), en -> en.isPickable() && !en.isSpectator() && !(en instanceof Projectile));
        for (int i = 0; i < MAX_STEPS; i++) {
            if (!arrow) velocity = step(velocity, pos, gravity, false);
            Vec3 next = pos.add(velocity);

            Vec3 hit = null;
            var block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (block.getType() != HitResult.Type.MISS) hit = block.getLocation();
            for (Entity en : targets) {
                var entityHit = en.getBoundingBox().inflate(HIT_MARGIN).clip(pos, hit != null ? hit : next);
                if (entityHit.isPresent()) hit = entityHit.get();
            }

            if (hit != null) {
                Gizmos.line(pos, hit, color, w).setAlwaysOnTop();
                Gizmos.cuboid(new AABB(hit, hit).inflate(MARKER), GizmoStyle.stroke(color, w)).setAlwaysOnTop();
                return hit;
            }
            Gizmos.line(pos, next, color, w).setAlwaysOnTop();
            pos = next;
            if (arrow) velocity = step(velocity, pos, gravity, true);
            if (pos.y < mc.level.getMinY() - 16) return null;
        }
        return null;
    }

    private Vec3 step(Vec3 velocity, Vec3 pos, double gravity, boolean arrow) {
        boolean water = !mc.level.getFluidState(BlockPos.containing(pos)).isEmpty();
        float inertia = water ? (arrow ? ARROW_WATER_INERTIA : THROWABLE_WATER_INERTIA) : AIR_INERTIA;
        if (arrow) return velocity.scale(inertia).subtract(0, gravity, 0);
        return velocity.subtract(0, gravity, 0).scale(inertia);
    }
}
