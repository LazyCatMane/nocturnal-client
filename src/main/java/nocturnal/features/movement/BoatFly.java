package nocturnal.features.movement;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;

/**
 * Flies the boat you're steering where you look. Jump goes up and sprint goes down, since sneak gets you out.
 * The server kicks vehicles that float for 80 ticks, so anti-kick drops slightly more than it allows every so often.
 */
public class BoatFly extends Module {
    private static final int KICK_INTERVAL = 40;
    /** The server counts a vehicle as floating unless it moves down more than 1/32 of a block in a tick. */
    private static final double DIP = 0.04;

    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal blocks per tick", 1, 0.1, 5, 0.05));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical speed", "Up/down blocks per tick", 0.4, 0, 2, 0.05));
    private final BoolSetting antiKick = add(new BoolSetting("Anti kick", "Dip down every 2 seconds so the server doesn't kick you", true));

    private int ticks;

    public BoatFly() {
        super("BoatFly", "Fly in boats", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return speed.display();
    }

    @Subscribe(priority = 10)
    private void onVehicleMove(Events.VehicleMove e) {
        if (!(e.vehicle instanceof AbstractBoat boat)) return;
        Vec3 horizontal = EntitySpeed.inputDirection().scale(speed.get());
        double y = mc.options.keyJump.isDown() ? verticalSpeed.get() : mc.options.keySprint.isDown() ? -verticalSpeed.get() : 0;
        if (antiKick.get() && ++ticks % KICK_INTERVAL == 0 && y > -DIP) y = -DIP;

        e.movement = new Vec3(horizontal.x, y, horizontal.z);
        boat.setDeltaMovement(e.movement);
        boat.setYRot(mc.player.getYRot());
    }
}
