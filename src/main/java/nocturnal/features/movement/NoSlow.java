package nocturnal.features.movement;

import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

public class NoSlow extends Module {
    private final BoolSetting items = add(new BoolSetting("Items", "No slowdown while eating, drawing a bow or blocking", true));
    private final BoolSetting sneak = add(new BoolSetting("Sneak", "Full speed while sneaking", true));
    private final BoolSetting blocks = add(new BoolSetting("Soul sand", "No slowdown on soul sand and honey", true));
    private final BoolSetting webs = add(new BoolSetting("Webs", "No slowdown in cobwebs, berry bushes and powder snow", true));

    public NoSlow() {
        super("NoSlow", "Removes movement slowdowns", Category.MOVEMENT);
    }

    @Subscribe
    private void onSlowdown(Events.Slowdown e) {
        boolean remove = switch (e.kind) {
            case ITEM_USE -> items.get();
            case SNEAK -> sneak.get();
            case BLOCK_SPEED -> blocks.get();
            case STUCK_IN_BLOCK -> webs.get();
        };
        if (remove) e.cancel();
    }
}
