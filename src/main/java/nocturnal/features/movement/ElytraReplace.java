package nocturnal.features.movement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Interact;

/** Swaps a worn-out elytra for the freshest one in your inventory, even mid-flight. */
public class ElytraReplace extends Module {
    /** Head, chest, legs, feet follow ARMOR_SLOT_START in the inventory screen. */
    private static final int CHEST_MENU_SLOT = InventoryMenu.ARMOR_SLOT_START + 1;

    private final NumberSetting durability = add(new NumberSetting("Durability", "Swap when the worn elytra has this percent left", 5, 1, 50, 1));

    public ElytraReplace() {
        super("ElytraReplace", "Swaps in a fresh elytra before yours breaks", Category.MOVEMENT);
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null) return;
        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!worn.is(Items.ELYTRA) || left(worn) * 100 > durability.get()) return;

        int best = -1;
        double bestLeft = durability.get() / 100;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.ELYTRA) && left(stack) > bestLeft) {
                bestLeft = left(stack);
                best = i;
            }
        }
        if (best >= 0) Interact.swapMenuSlots(Interact.menuSlot(best), CHEST_MENU_SLOT);
    }

    private static double left(ItemStack stack) {
        return 1 - (double) stack.getDamageValue() / stack.getMaxDamage();
    }
}
