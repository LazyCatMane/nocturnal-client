package nocturnal.features.movement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Baritone;

/** Control mode: no gravity, WASD moves horizontally, jump and sneak move up and down. */
public class ElytraFly extends Module {
    private static final double STUCK_DIST = 0.08;
    private static final int STUCK_TICKS = 8;
    private static final int UNSTUCK_TICKS = 20;
    private static final int RESET_COOLDOWN = 80;

    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal blocks per tick", 1.5, 0.1, 5, 0.05));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical speed", "Up/down blocks per tick", 0.5, 0, 3, 0.05));
    private final BoolSetting baritone = add(new BoolSetting("Baritone", "Follow #elytra's 3D path. WASD still steers if you touch it", true));

    private Vec3 lastPos;
    private int stuck;
    private int unstuck;
    private int resetWait;

    public ElytraFly() {
        super("ElytraFly", "Controlled elytra flight", Category.MOVEMENT);
    }

    @Override
    public String info() {
        if (!following()) return speed.display();
        if (!Baritone.nether() && !Baritone.elytraFlying()) return "Nether";
        if (resetWait > 0) return "Reset";
        if (unstuck > 0 || stuck >= STUCK_TICKS) return "Unstuck";
        return Baritone.hasFlyPath() ? "Path" : "Calc";
    }

    @Override
    protected void onDisable() {
        clearStuck();
    }

    @Subscribe
    private void onFirework(Events.BaritoneFirework e) {
        if (baritone.get()) e.cancel();
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || !following()) {
            clearStuck();
            return;
        }
        if (Baritone.atGoal()) {
            Baritone.stop();
            clearStuck();
            return;
        }
        Baritone.prepareFlight();
        if (mc.player.isFallFlying()) {
            tickStuck();
            return;
        }
        clearStuck();
        if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        if (mc.player.onGround()) mc.player.jumpFromGround();
        else mc.options.keyJump.setDown(true);
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        if (!mc.player.isFallFlying()) return;
        Vec2 input = mc.player.input.getMoveVector();
        Vec3 movement = input.lengthSquared() > 0 || !following() ? manual(input) : followPath();
        e.movement = movement;
        mc.player.setDeltaMovement(movement);
    }

    private Vec3 manual(Vec2 input) {
        double rad = Math.toRadians(mc.player.getYRot());
        Vec3 horizontal = new Vec3(-Math.sin(rad) * input.y + Math.cos(rad) * input.x, 0, Math.cos(rad) * input.y + Math.sin(rad) * input.x);
        if (horizontal.lengthSqr() > 0) horizontal = horizontal.normalize().scale(speed.get());
        double y = mc.options.keyJump.isDown() ? verticalSpeed.get() : mc.options.keyShift.isDown() ? -verticalSpeed.get() : 0;
        return new Vec3(horizontal.x, y, horizontal.z);
    }

    /** Fly to the next #elytra node. Keep moving while the next path segment is still calculating. */
    private Vec3 followPath() {
        if (resetWait == 0 && stuck >= STUCK_TICKS && unstuck == 0) unstuck = UNSTUCK_TICKS;
        if (unstuck > 0) return breakStuck(wishToward(Baritone.flyTarget()));
        Vec3 wish = wishToward(Baritone.flyTarget());
        if (wish == null) return unstuck > 0 || resetWait > 0 ? breakStuck(null) : Vec3.ZERO;
        return slide(wish);
    }

    private Vec3 wishToward(Vec3 target) {
        if (target == null) return null;
        Vec3 delta = target.subtract(mc.player.position());
        if (delta.lengthSqr() < 1e-6) return null;
        Vec3 dir = delta.normalize();
        double cap = speed.get();
        double vy = dir.y * cap;
        if (Math.abs(vy) > verticalSpeed.get()) vy = Math.copySign(verticalSpeed.get(), vy);
        return new Vec3(dir.x * cap, vy, dir.z * cap);
    }

    /**
     * Same idea as tapping W: fly the way you're looking, plus a few sidesteps if that is blocked.
     * Path follow can sit at a node and return zero; this must still move.
     */
    private Vec3 breakStuck(Vec3 wish) {
        AABB box = mc.player.getBoundingBox();
        double step = Math.max(speed.get(), 0.8);
        Vec3 look = manual(new Vec2(0, 1));
        double up = Math.max(verticalSpeed.get(), 0.6);
        Vec3 toward = wish != null && wish.lengthSqr() > 0 ? wish.normalize().scale(step) : look;
        Vec3[] tries = {
            look, look.add(0, up, 0), toward, new Vec3(toward.x, up, toward.z),
            new Vec3(0, up, 0), new Vec3(0, -up, 0),
            new Vec3(step, 0, 0), new Vec3(-step, 0, 0), new Vec3(0, 0, step), new Vec3(0, 0, -step)
        };
        for (Vec3 tryMove : tries) if (tryMove.lengthSqr() > 0 && fits(box, tryMove)) return tryMove;
        return look.lengthSqr() > 0 ? look : new Vec3(0, up, 0);
    }

    private void tickStuck() {
        Vec3 pos = mc.player.position();
        boolean still = lastPos != null && pos.distanceTo(lastPos) < STUCK_DIST;
        if (still) stuck++;
        else stuck = 0;
        if (unstuck > 0) {
            unstuck--;
            if (unstuck == 0 && still) {
                Baritone.resetElytra();
                stuck = 0;
                resetWait = RESET_COOLDOWN;
            }
        }
        if (resetWait > 0) resetWait--;
        lastPos = pos;
    }

    private void clearStuck() {
        lastPos = null;
        stuck = 0;
        unstuck = 0;
        resetWait = 0;
    }

    /** Move as much of the wish vector as fits, sliding along walls instead of stopping. */
    private Vec3 slide(Vec3 wish) {
        AABB box = mc.player.getBoundingBox();
        if (!mc.level.noCollision(mc.player, box)) return unstick();
        if (mc.level.noCollision(mc.player, box.move(wish))) return wish;
        boolean upFirst = Math.abs(wish.y) >= Math.max(Math.abs(wish.x), Math.abs(wish.z));
        Vec3[] parts = upFirst
            ? new Vec3[]{new Vec3(0, wish.y, 0), new Vec3(wish.x, 0, 0), new Vec3(0, 0, wish.z)}
            : new Vec3[]{new Vec3(wish.x, 0, 0), new Vec3(0, 0, wish.z), new Vec3(0, wish.y, 0)};
        Vec3 out = Vec3.ZERO;
        for (Vec3 part : parts) if (fits(box, out.add(part))) out = out.add(part);
        return out.lengthSqr() > 0 ? out : unstick();
    }

    private Vec3 unstick() {
        AABB box = mc.player.getBoundingBox();
        Vec3[] tries = {
            new Vec3(0, 0.4, 0), new Vec3(0, -0.4, 0),
            new Vec3(0.4, 0, 0), new Vec3(-0.4, 0, 0), new Vec3(0, 0, 0.4), new Vec3(0, 0, -0.4),
            new Vec3(0.4, 0.3, 0), new Vec3(-0.4, 0.3, 0), new Vec3(0, 0.3, 0.4), new Vec3(0, 0.3, -0.4)
        };
        for (Vec3 step : tries) if (fits(box, step)) return step;
        return Vec3.ZERO;
    }

    private boolean fits(AABB box, Vec3 step) {
        return mc.level.noCollision(mc.player, box.move(step));
    }

    private boolean following() {
        return baritone.get() && Baritone.hasGoal();
    }
}
