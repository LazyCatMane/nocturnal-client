package nocturnal.features.misc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;

import java.util.Set;

/** Eats the best food in the hotbar, then switches back to the slot you had. */
public class AutoEat extends Module {
    private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
        Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW);
    private static final Set<Item> GAPPLES = Set.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

    private final NumberSetting hunger = add(new NumberSetting("Hunger", "Eat when the hunger bar is below this", 14, 1, 19, 1));
    private final BoolSetting lowHealth = add(new BoolSetting("Low health", "Also eat when health is low, preferring golden apples", true));
    private final NumberSetting health = add(new NumberSetting("Health", "Health (including absorption) that counts as low", 10, 1, 19, 1));
    private final BoolSetting saveGapples = add(new BoolSetting("Save gapples", "Only eat golden apples when health is low", true));

    private int previousSlot = -1;

    public AutoEat() {
        super("AutoEat", "Eats automatically", Category.MISC);
    }

    @Override
    public String info() {
        return previousSlot >= 0 ? "Eating" : null;
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (!inGame()) return;
        boolean danger = lowHealth.get() && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.get();
        boolean hungry = mc.player.getFoodData().getFoodLevel() < hunger.get();
        int slot = danger || hungry ? bestSlot(danger) : -1;
        if (slot < 0) {
            stopEating();
            return;
        }
        Inventory inv = mc.player.getInventory();
        if (previousSlot < 0) previousSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.options.keyUse.setDown(true);
    }

    @Override
    protected void onDisable() {
        stopEating();
    }

    private void stopEating() {
        if (previousSlot < 0) return;
        mc.options.keyUse.setDown(false);
        if (inGame()) mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    private int bestSlot(boolean danger) {
        boolean canEatNormalFood = mc.player.getFoodData().needsFood();
        int best = -1;
        float bestScore = -1;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || BAD_FOOD.contains(stack.getItem())) continue;
            if (!canEatNormalFood && !food.canAlwaysEat()) continue;
            boolean gapple = GAPPLES.contains(stack.getItem());
            if (gapple && saveGapples.get() && !danger) continue;
            float score = food.saturation() + (danger && gapple ? 100 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
