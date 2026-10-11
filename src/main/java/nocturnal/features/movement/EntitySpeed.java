package nocturnal.features.movement;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.NumberSetting;

/** Sets the horizontal speed of whatever you're riding: horses, pigs, striders, camels and boats. */
public class EntitySpeed extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal blocks per tick", 1, 0.1, 5, 0.05));

    public EntitySpeed() {
        super("EntitySpeed", "Faster horses, boats and other rides", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return speed.display();
    }

    @Subscribe
    private void onVehicleMove(Events.VehicleMove e) {
        if (e.vehicle instanceof AbstractBoat && Modules.get(BoatFly.class).isEnabled()) return;
        Vec3 horizontal = inputDirection();
        if (horizontal.lengthSqr() == 0) return;
        horizontal = horizontal.scale(speed.get());
        e.movement = new Vec3(horizontal.x, e.movement.y, horizontal.z);
        e.vehicle.setDeltaMovement(horizontal.x, e.vehicle.getDeltaMovement().y, horizontal.z);
    }

    /** Unit vector of the movement keys relative to where you're looking, or zero when none are held. */
    static Vec3 inputDirection() {
        Vec2 input = mc.player.input.getMoveVector();
        if (input.lengthSquared() == 0) return Vec3.ZERO;
        double rad = Math.toRadians(mc.player.getYRot());
        return new Vec3(-Math.sin(rad) * input.y + Math.cos(rad) * input.x, 0, Math.cos(rad) * input.y + Math.sin(rad) * input.x).normalize();
    }
}
