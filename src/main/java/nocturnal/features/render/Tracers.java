package nocturnal.features.render;

import net.minecraft.client.Camera;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.NumberSetting;
import org.joml.Vector3fc;

/** Lines from your crosshair to everything ESP targets, using ESP's colors. */
public class Tracers extends Module {
    private final NumberSetting width = add(new NumberSetting("Line width", "Line thickness", 1.5, 1, 5, 0.5));

    public Tracers() {
        super("Tracers", "Lines to the targets enabled in ESP", Category.RENDER);
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (!inGame()) return;
        Esp esp = Modules.get(Esp.class);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3fc f = camera.forwardVector();
        Vec3 start = camera.position().add(f.x(), f.y(), f.z());
        float w = width.get().floatValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            int color = esp.colorOf(entity);
            if (color != 0) Gizmos.line(start, Esp.boxOf(entity, e.tickDelta).getCenter(), color, w).setAlwaysOnTop();
        }
        for (BlockEntity be : esp.storage()) {
            Gizmos.line(start, Esp.boxOf(be).getCenter(), esp.storageColor(), w).setAlwaysOnTop();
        }
    }
}
