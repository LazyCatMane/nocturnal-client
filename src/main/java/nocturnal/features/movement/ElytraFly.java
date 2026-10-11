package nocturnal.features.movement;

import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;

/** Control mode: no gravity, WASD moves horizontally, jump and sneak move up and down. */
public class ElytraFly extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal blocks per tick", 1.5, 0.1, 5, 0.05));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical speed", "Up/down blocks per tick", 0.5, 0, 3, 0.05));

    public ElytraFly() {
        super("ElytraFly", "Controlled elytra flight", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return speed.display();
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        if (!mc.player.isFallFlying()) return;
        Vec2 input = mc.player.input.getMoveVector();
        double rad = Math.toRadians(mc.player.getYRot());
        Vec3 horizontal = new Vec3(-Math.sin(rad) * input.y + Math.cos(rad) * input.x, 0, Math.cos(rad) * input.y + Math.sin(rad) * input.x);
        if (horizontal.lengthSqr() > 0) horizontal = horizontal.normalize().scale(speed.get());
        double y = mc.options.keyJump.isDown() ? verticalSpeed.get() : mc.options.keyShift.isDown() ? -verticalSpeed.get() : 0;

        Vec3 movement = new Vec3(horizontal.x, y, horizontal.z);
        e.movement = movement;
        mc.player.setDeltaMovement(movement);
    }
}
