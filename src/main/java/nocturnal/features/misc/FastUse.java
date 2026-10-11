package nocturnal.features.misc;

import net.minecraft.world.item.BlockItem;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.MinecraftAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;

/** Shortens vanilla's 4-tick wait between right clicks, for placing blocks and throwing items faster. */
public class FastUse extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between right clicks (vanilla is 4)", 0, 0, 4, 1));
    private final BoolSetting blocksOnly = add(new BoolSetting("Blocks only", "Only speed up placing blocks", false));

    public FastUse() {
        super("FastUse", "No right-click delay", Category.MISC);
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame()) return;
        if (blocksOnly.get() && !(mc.player.getMainHandItem().getItem() instanceof BlockItem) && !(mc.player.getOffhandItem().getItem() instanceof BlockItem)) return;
        MinecraftAccessor game = (MinecraftAccessor) mc;
        if (game.nocturnal$rightClickDelay() > delay.getInt()) game.nocturnal$setRightClickDelay(delay.getInt());
    }
}
