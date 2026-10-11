package nocturnal.features.render;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.CameraAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;

/** Orbits a third-person camera around you with the mouse while you keep walking the way you were facing. */
public class Freelook extends Module {
    private static final double MOUSE_SENSITIVITY = 0.15;
    /** Keeps the camera this far in front of a wall it backs into. */
    private static final double WALL_GAP = 0.1;

    private final NumberSetting distance = add(new NumberSetting("Distance", "Camera distance from you", 4, 1, 12, 0.5));

    private float yaw, pitch;

    public Freelook() {
        super("Freelook", "Look around without turning", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
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
    private void onCameraSetup(Events.CameraSetup e) {
        e.detached = true;
    }

    @Subscribe
    private void onCameraUpdate(Events.CameraUpdate e) {
        Vec3 eye = mc.player.getEyePosition(e.tickDelta);
        Vec3 back = Vec3.directionFromRotation(pitch, yaw).scale(-distance.get());
        var hit = mc.level.clip(new ClipContext(eye, eye.add(back), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        double reach = hit.getType() == HitResult.Type.MISS ? distance.get() : Math.max(0, hit.getLocation().distanceTo(eye) - WALL_GAP);

        CameraAccessor camera = (CameraAccessor) e.camera;
        camera.nocturnal$setPosition(eye.add(back.normalize().scale(reach)));
        camera.nocturnal$setRotation(yaw, pitch);
    }
}
