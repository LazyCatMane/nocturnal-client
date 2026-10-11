package nocturnal.features.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.util.Combat;
import nocturnal.util.Interact;

/**
 * Holds the sword or axe with the highest damage per hit. Switching resets the attack charge, so it stays on the
 * weapon afterwards, and KillAura switches as soon as it has a target rather than at the moment it swings.
 */
public class AutoWeapon extends Module {
    private final BoolSetting inventory = add(new BoolSetting("Inventory", "Also use weapons from your main inventory, not just the hotbar", true));

    public AutoWeapon() {
        super("AutoWeapon", "Switches to your best sword or axe when attacking", Category.COMBAT);
    }

    @Subscribe
    private void onAttack(Events.Attack e) {
        if (e.target() instanceof LivingEntity) equipBest();
    }

    public void equipBest() {
        Inventory inv = mc.player.getInventory();
        int selected = inv.getSelectedSlot();
        int best = -1;
        double bestDamage = damage(inv.getItem(selected));
        int size = inventory.get() ? Inventory.INVENTORY_SIZE : Inventory.getSelectionSize();
        for (int i = 0; i < size; i++) {
            double d = damage(inv.getItem(i));
            if (d > bestDamage) {
                bestDamage = d;
                best = i;
            }
        }
        if (best < 0) return;
        if (best < Inventory.getSelectionSize()) inv.setSelectedSlot(best);
        else Interact.swapWithHotbar(best, selected);
    }

    /** Damage per hit from the item's attack modifiers plus Sharpness, or 0 for anything that isn't a sword or axe. */
    private static double damage(ItemStack stack) {
        if (!stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.AXES)) return 0;
        double damage = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
        int sharpness = Combat.enchantLevel(Enchantments.SHARPNESS, stack);
        return sharpness > 0 ? damage + 0.5 * sharpness + 0.5 : damage;
    }
}
