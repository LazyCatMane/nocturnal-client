package nocturnal.features.combat;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;

public class AutoTotem extends Module {
    public enum Mode { ALWAYS, HEALTH }

    /** Swap-click button that targets the offhand slot. */
    private static final int OFFHAND_BUTTON = 40;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Always keeps a totem in offhand, Health only below the threshold", Mode.ALWAYS));
    private final NumberSetting health = add(new NumberSetting("Health", "Health (including absorption) at or below which Health mode swaps", 10, 1, 36, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait between swaps", 1, 0, 20, 1));
    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your offhand", Category.COMBAT);
    }

    @Override
    public String info() {
        return String.valueOf(mc.player == null ? 0 : mc.player.getInventory().countItem(Items.TOTEM_OF_UNDYING));
    }

    @Override
    protected void onEnable() {
        Modules.get(Offhand.class).setEnabled(false);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame() || cooldown-- > 0) return;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || mc.screen instanceof CreativeModeInventoryScreen) return;
        if (mode.get() == Mode.HEALTH && mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.get()) return;

        AbstractContainerMenu menu = mc.player.containerMenu;
        for (Slot slot : menu.slots) {
            if (slot.container == mc.player.getInventory() && slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, OFFHAND_BUTTON, ClickType.SWAP, mc.player);
                cooldown = delay.getInt();
                return;
            }
        }
    }
}
