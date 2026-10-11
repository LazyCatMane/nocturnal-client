package nocturnal.features.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ListSetting;

import java.util.Set;

/**
 * Hides every block except the listed ones. Servers with anti-xray (Paper's engine-mode) send fake ores in solid
 * stone, so this is most reliable for ores exposed to caves and for blocks anti-xray doesn't hide.
 */
public class XRay extends Module {
    private final ListSetting<Block> visible = add(new ListSetting<>("Blocks", "Blocks that stay visible", BuiltInRegistries.BLOCK,
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER, Blocks.CHEST, Blocks.LAVA));
    private final BoolSetting fullbright = add(new BoolSetting("Fullbright", "Turn on Fullbright too, since ores in dark caves are black", true));

    /** Read by the chunk building threads. */
    private volatile Set<Block> applied;
    private boolean enabledFullbright;

    public XRay() {
        super("XRay", "See only chosen blocks", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        applied = visible.get();
        Fullbright light = Modules.get(Fullbright.class);
        enabledFullbright = fullbright.get() && !light.isEnabled();
        if (enabledFullbright) light.setEnabled(true);
        reload();
    }

    @Override
    protected void onDisable() {
        if (enabledFullbright) Modules.get(Fullbright.class).setEnabled(false);
        reload();
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (visible.get() != applied) {
            applied = visible.get();
            reload();
        }
    }

    @Subscribe
    private void onVisibility(Events.BlockVisibility e) {
        if (!applied.contains(e.state.getBlock())) e.cancel();
    }

    private static void reload() {
        if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
    }
}
