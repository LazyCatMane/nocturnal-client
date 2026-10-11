package nocturnal.features.misc;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import nocturnal.util.Interact;
import nocturnal.util.Rotations;

import java.util.function.Predicate;

/** Throws XP bottles at your feet until every Mending item you wear or hold is repaired, then turns off. */
public class AutoMend extends Module {
    private static final int ROTATION_PRIORITY = 15;
    private static final Predicate<ItemStack> XP = s -> s.is(Items.EXPERIENCE_BOTTLE);

    private final NumberSetting bottles = add(new NumberSetting("Bottles per tick", "XP bottles thrown each tick", 1, 1, 5, 1));
    private final BoolSetting inventory = add(new BoolSetting("Inventory", "Move XP from your main inventory to the hotbar when the hotbar runs out", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Look down server-side only", true));
    private final EnumSetting<Interact.Switch> switchMode = add(new EnumSetting<>("Switch", "Normal selects the XP, Silent switches back in the same tick", Interact.Switch.SILENT));

    private boolean thrown;

    public AutoMend() {
        super("AutoMend", "Throws XP until your Mending gear is repaired", Category.MISC);
    }

    @Override
    protected void onEnable() {
        thrown = false;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame() || mc.gameMode == null) return;
        if (!needsMending()) {
            if (!thrown) log("Nothing with Mending needs repair");
            setEnabled(false);
            return;
        }
        if (Interact.handFor(XP, switchMode.get()) == null && !(inventory.get() && pullToHotbar())) {
            log("No XP bottles left");
            setEnabled(false);
            return;
        }

        Runnable act = () -> {
            thrown = true;
            for (int i = 0; i < bottles.getInt(); i++) {
                if (!Interact.use(XP, switchMode.get())) break;
            }
        };
        if (rotate.get()) Rotations.rotate(mc.player.getYRot(), 90, ROTATION_PRIORITY, act);
        else act.run();
    }

    private boolean needsMending() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isDamaged() && Combat.enchantLevel(Enchantments.MENDING, stack) > 0) return true;
        }
        return false;
    }

    /** Moves a stack of XP into an empty hotbar slot, or the last one if the hotbar is full. */
    private boolean pullToHotbar() {
        int from = Interact.inventorySlot(XP);
        if (from < Inventory.getSelectionSize()) return false;
        Inventory inv = mc.player.getInventory();
        int to = Inventory.getSelectionSize() - 1;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inv.getItem(i).isEmpty() && i != inv.getSelectedSlot()) {
                to = i;
                break;
            }
        }
        return Interact.swapWithHotbar(from, to);
    }
}
