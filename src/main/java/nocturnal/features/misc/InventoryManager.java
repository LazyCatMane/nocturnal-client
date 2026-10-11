package nocturnal.features.misc;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.ListSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.setting.StringSetting;
import nocturnal.util.Interact;

import java.util.ArrayList;
import java.util.List;

/** Keeps items in their hotbar slots, tops up running-low hotbar stacks and throws out junk. One move at a time. */
public class InventoryManager extends Module {
    private static final int HOTBAR = Inventory.getSelectionSize();
    private static final String ANY = "-";
    /** Drop button for THROW clicks that throws the whole stack. */
    private static final int WHOLE_STACK = 1;

    private final StringSetting layout = add(new StringSetting("Layout", "Item per hotbar slot, comma separated, - for any. Easiest set with .layout save",
        String.join(",", List.of(ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY))));
    private final BoolSetting refill = add(new BoolSetting("Refill", "Top up hotbar stacks from your inventory when they run low", true));
    private final NumberSetting refillBelow = add(new NumberSetting("Refill below", "Refill when a stack is below this percent full", 50, 5, 95, 5));
    private final BoolSetting dropJunk = add(new BoolSetting("Drop junk", "Throw out items in the junk list", true));
    private final ListSetting<Item> junk = add(new ListSetting<>("Junk", "Items to throw out", BuiltInRegistries.ITEM, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between inventory moves", 2, 0, 20, 1));

    private int timer;

    public InventoryManager() {
        super("InventoryManager", "Hotbar layout, refill and junk dropping", Category.MISC);
    }

    /** Remembers the current hotbar as the layout. */
    public void saveLayout() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < HOTBAR; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            ids.add(stack.isEmpty() ? ANY : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        }
        layout.set(String.join(",", ids));
    }

    public void clearLayout() {
        layout.reset();
    }

    public String layoutText() {
        return layout.get();
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame() || mc.gameMode == null) return;
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) return;
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (timer++ < delay.getInt()) return;
        Item[] wanted = wantedHotbar();
        if (arrange(wanted) || (refill.get() && refill()) || (dropJunk.get() && dropJunk(wanted))) timer = 0;
    }

    private boolean arrange(Item[] wanted) {
        Inventory inv = mc.player.getInventory();
        for (int slot = 0; slot < HOTBAR; slot++) {
            Item item = wanted[slot];
            if (item == null || inv.getItem(slot).is(item)) continue;
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                if (i == slot || !inv.getItem(i).is(item) || (i < HOTBAR && wanted[i] == item)) continue;
                return Interact.swapWithHotbar(i, slot);
            }
        }
        return false;
    }

    private boolean refill() {
        Inventory inv = mc.player.getInventory();
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !stack.isStackable() || stack.getCount() * 100 >= stack.getMaxStackSize() * refillBelow.get()) continue;
            for (int i = HOTBAR; i < Inventory.INVENTORY_SIZE; i++) {
                if (ItemStack.isSameItemSameComponents(stack, inv.getItem(i))) {
                    return Interact.swapMenuSlots(Interact.menuSlot(i), Interact.menuSlot(slot));
                }
            }
        }
        return false;
    }

    private boolean dropJunk(Item[] wanted) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !junk.contains(stack.getItem()) || (i < HOTBAR && wanted[i] == stack.getItem())) continue;
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, Interact.menuSlot(i), WHOLE_STACK, ClickType.THROW, mc.player);
            return true;
        }
        return false;
    }

    private Item[] wantedHotbar() {
        Item[] wanted = new Item[HOTBAR];
        String[] parts = layout.get().split(",");
        for (int i = 0; i < Math.min(parts.length, HOTBAR); i++) {
            String id = parts[i].trim().toLowerCase();
            if (id.isEmpty() || id.equals(ANY)) continue;
            Identifier key = Identifier.tryParse(id);
            if (key != null) wanted[i] = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        }
        return wanted;
    }
}
