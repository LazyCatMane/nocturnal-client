package nocturnal.features.misc;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.MultiPlayerGameModeAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.util.Combat;
import nocturnal.util.Interact;

/** Switches to the fastest tool while mining and back to what you held when you stop. */
public class AutoTool extends Module {
    /** Vanilla mines with the wrong tool about this many times slower. */
    private static final float WRONG_TOOL_PENALTY = 0.3F;

    private final BoolSetting inventory = add(new BoolSetting("Inventory", "Also use tools from your main inventory, not just the hotbar", true));

    /** Hotbar slot we held before switching, and the inventory index swapped into it, or -1. */
    private int restoreSlot = -1, swappedIndex = -1;

    public AutoTool() {
        super("AutoTool", "Switches to the fastest tool while mining", Category.MISC);
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @Subscribe
    private void onMining(Events.Mining e) {
        BlockState state = mc.level.getBlockState(e.pos());
        if (state.isAir()) return;
        Inventory inv = mc.player.getInventory();
        int selected = inv.getSelectedSlot();
        int best = bestTool(state, inventory.get());
        if (best < 0) return;

        if (restoreSlot < 0) restoreSlot = selected;
        if (best < Inventory.getSelectionSize()) {
            inv.setSelectedSlot(best);
            ((MultiPlayerGameModeAccessor) mc.gameMode).nocturnal$syncSelectedSlot();
            return;
        }
        if (swappedIndex >= 0) Interact.swapWithHotbar(swappedIndex, selected);
        if (Interact.swapWithHotbar(best, selected)) swappedIndex = best;
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (restoreSlot >= 0 && inGame() && !mc.gameMode.isDestroying() && !mc.options.keyAttack.isDown()) restore();
    }

    private void restore() {
        if (!inGame()) {
            restoreSlot = swappedIndex = -1;
            return;
        }
        Inventory inv = mc.player.getInventory();
        if (swappedIndex >= 0) Interact.swapWithHotbar(swappedIndex, restoreSlot);
        if (restoreSlot >= 0) inv.setSelectedSlot(restoreSlot);
        restoreSlot = swappedIndex = -1;
    }

    /** Inventory index of a tool that mines the block faster than the held item, or -1. */
    public static int bestTool(BlockState state, boolean includeInventory) {
        Inventory inv = mc.player.getInventory();
        int best = -1;
        float bestSpeed = speed(inv.getItem(inv.getSelectedSlot()), state);
        int size = includeInventory ? Inventory.INVENTORY_SIZE : Inventory.getSelectionSize();
        for (int i = 0; i < size; i++) {
            float s = speed(inv.getItem(i), state);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        return best;
    }

    private static float speed(ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        if (speed > 1) {
            int efficiency = Combat.enchantLevel(Enchantments.EFFICIENCY, stack);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }
        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) speed *= WRONG_TOOL_PENALTY;
        return speed;
    }
}
