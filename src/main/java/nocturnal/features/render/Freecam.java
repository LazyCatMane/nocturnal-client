package nocturnal.features.render;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.CameraAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;

/** Flies the camera with the movement keys while your body stays put (it still falls if in the air). */
public class Freecam extends Module {
    private static final double MOUSE_SENSITIVITY = 0.15;

    private final NumberSetting speed = add(new NumberSetting("Speed", "Blocks per tick", 1, 0.1, 5, 0.1));

    private Vec3 pos, prevPos;
    private float yaw, pitch;

    public Freecam() {
        super("Freecam", "Moves the camera freely", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        pos = prevPos = mc.player.getEyePosition();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
    }

    @Subscribe
    private void onTurn(Events.Turn e) {
        e.cancel();
        yaw += (float) (e.yaw * MOUSE_SENSITIVITY);
        pitch = Mth.clamp(pitch + (float) (e.pitch * MOUSE_SENSITIVITY), -90, 90);
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame()) return;
        prevPos = pos;
        double forward = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        double strafe = axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
        double up = axis(mc.options.keyJump.isDown(), mc.options.keyShift.isDown());
        double rad = Math.toRadians(yaw);
        Vec3 horizontal = new Vec3(-Math.sin(rad) * forward + Math.cos(rad) * strafe, 0, Math.cos(rad) * forward + Math.sin(rad) * strafe);
        if (horizontal.lengthSqr() > 0) horizontal = horizontal.normalize();
        pos = pos.add(horizontal.add(0, up, 0).scale(speed.get()));
    }

    @Subscribe
    private void onMove(Events.PlayerMove e) {
        e.movement = new Vec3(0, Math.min(e.movement.y, 0), 0);
    }

    @Subscribe
    private void onCameraSetup(Events.CameraSetup e) {
        e.detached = true;
    }

    @Subscribe
    private void onCameraUpdate(Events.CameraUpdate e) {
        CameraAccessor camera = (CameraAccessor) e.camera;
        camera.nocturnal$setPosition(prevPos.lerp(pos, e.tickDelta));
        camera.nocturnal$setRotation(yaw, pitch);
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }
}
