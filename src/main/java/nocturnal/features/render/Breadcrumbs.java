package nocturnal.features.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.ColorSetting;
import nocturnal.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.Deque;

/** A line along the path you've walked or flown, until you change dimension or turn it off. */
public class Breadcrumbs extends Module {
    /** A new point is added once you've moved this far from the last one. */
    private static final double STEP = 0.5;

    private final NumberSetting maxPoints = add(new NumberSetting("Max points", "Oldest points are dropped past this", 2000, 100, 10000, 100));
    private final ColorSetting color = add(new ColorSetting("Color", "Trail color", 0xFFA855F7));
    private final NumberSetting width = add(new NumberSetting("Line width", "Line thickness", 2, 1, 5, 0.5));

    private final Deque<Vec3> points = new ArrayDeque<>();
    private ClientLevel level;

    public Breadcrumbs() {
        super("Breadcrumbs", "Shows the path you've travelled", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        points.clear();
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame()) return;
        if (mc.level != level) {
            level = mc.level;
            points.clear();
        }
        Vec3 pos = mc.player.position();
        if (points.isEmpty() || points.peekLast().distanceToSqr(pos) > STEP * STEP) points.addLast(pos);
        while (points.size() > maxPoints.getInt()) points.removeFirst();
    }

    @Subscribe
    private void onRender(Events.Render3D e) {
        if (points.isEmpty() || !inGame()) return;
        int c = color.get();
        float w = width.get().floatValue();
        Vec3 prev = null;
        for (Vec3 p : points) {
            if (prev != null) Gizmos.line(prev, p, c, w);
            prev = p;
        }
        Gizmos.line(prev, mc.player.getPosition(e.tickDelta), c, w);
    }
}
