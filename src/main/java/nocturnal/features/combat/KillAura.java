package nocturnal.features.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import nocturnal.util.Rotations;

import java.util.Comparator;

public class KillAura extends Module {
    public enum Priority { CLOSEST, HEALTH, ANGLE }

    private static final int ROTATION_PRIORITY = 10;

    private final NumberSetting range = add(new NumberSetting("Range", "Attack reach, measured to the target's hitbox", 4, 1, 6, 0.1));
    private final NumberSetting wallsRange = add(new NumberSetting("Walls range", "Reach when the target is behind blocks", 3, 0, 6, 0.1));
    private final BoolSetting players = add(new BoolSetting("Players", "Attack players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Attack hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Passives", "Attack animals and other passive mobs", false));
    private final EnumSetting<Priority> priority = add(new EnumSetting<>("Priority", "Which target to pick first", Priority.CLOSEST));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face the target server-side; your view doesn't move", true));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown", "Wait for a fully charged hit (1.9+ combat)", true));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between hits when Cooldown is off", 2, 0, 20, 1));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause on use", "Don't attack while eating, drinking or blocking", true));

    private LivingEntity target;
    private int ticksSinceHit;

    public KillAura() {
        super("KillAura", "Attacks nearby targets", Category.COMBAT);
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
        ticksSinceHit++;
        target = null;
        if (!inGame() || mc.gameMode == null || (pauseOnUse.get() && mc.player.isUsingItem())) return;
        target = findTarget();
        if (target == null) return;
        AutoWeapon weapon = Modules.get(AutoWeapon.class);
        if (weapon.isEnabled()) weapon.equipBest();

        boolean ready = cooldown.get() ? mc.player.getAttackStrengthScale(0.5F) >= 1 : ticksSinceHit >= delay.getInt();
        Criticals criticals = Modules.get(Criticals.class);
        if (ready && criticals.isEnabled()) ready = criticals.readyToHit();
        LivingEntity hit = target;
        Runnable attack = ready ? () -> attack(hit) : null;
        if (rotate.get()) Rotations.rotate(Combat.aimPoint(hit.getBoundingBox()), ROTATION_PRIORITY, attack);
        else if (attack != null) attack.run();
    }

    private void attack(LivingEntity entity) {
        if (!entity.isAlive() || mc.gameMode == null) return;
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ticksSinceHit = 0;
    }

    private LivingEntity findTarget() {
        double max = Math.max(range.get(), wallsRange.get());
        Comparator<LivingEntity> order = switch (priority.get()) {
            case CLOSEST -> Comparator.comparingDouble(t -> Combat.reach(t.getBoundingBox()));
            case HEALTH -> Comparator.comparingDouble(Combat::health);
            case ANGLE -> Comparator.comparingDouble(this::angleTo);
        };
        return mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(max + 1), this::isTarget).stream()
            .map(t -> (LivingEntity) t)
            .min(order)
            .orElse(null);
    }

    private boolean isTarget(Entity e) {
        if (!Combat.isValid(e)) return false;
        boolean wanted = e instanceof Player ? players.get() : Combat.isHostile(e) ? hostiles.get() : Combat.isPassive(e) && passives.get();
        if (!wanted) return false;
        double reach = Combat.reach(e.getBoundingBox());
        return reach <= range.get() && (reach <= wallsRange.get() || mc.player.hasLineOfSight(e));
    }

    private double angleTo(LivingEntity e) {
        Vec3 to = e.getBoundingBox().getCenter().subtract(mc.player.getEyePosition()).normalize();
        return Math.acos(Math.max(-1, Math.min(1, mc.player.getViewVector(1).dot(to))));
    }
}
