package nocturnal.features.misc;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.AbstractContainerScreenAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

/** Shulker contents in tooltips, middle-click to look inside one, and Steal/Dump buttons on chests and shulkers. */
public class ShulkerTools extends Module {
    private static final int SHULKER_SIZE = 27, COLUMNS = 9, CELL = 18;
    private static final int PREVIEW_BACKGROUND = 0xC0100C18;
    private static final int MIDDLE_CLICK = 2;
    private static final int BUTTON_WIDTH = 40, BUTTON_HEIGHT = 16;

    private final BoolSetting preview = add(new BoolSetting("Preview", "Show what's inside a shulker when you hover it", true));
    private final BoolSetting peek = add(new BoolSetting("Peek", "Middle-click a shulker in any inventory to look inside", true));
    private final BoolSetting buttons = add(new BoolSetting("Buttons", "Steal and Dump buttons on chests, barrels and shulkers", true));

    private record Contents(NonNullList<ItemStack> items) implements TooltipComponent {}

    public ShulkerTools() {
        super("ShulkerTools", "Shulker preview, peek, and steal/dump buttons", Category.MISC);
        TooltipComponentCallback.EVENT.register(data -> data instanceof Contents c ? new PreviewTooltip(c.items) : null);
    }

    @Subscribe
    private void onTooltip(Events.TooltipImage e) {
        if (!preview.get()) return;
        NonNullList<ItemStack> items = contents(e.stack);
        if (items != null && items.stream().anyMatch(s -> !s.isEmpty())) e.image = new Contents(items);
    }

    @Subscribe
    private void onSlotClick(Events.SlotClick e) {
        if (!peek.get() || e.button != MIDDLE_CLICK) return;
        NonNullList<ItemStack> items = contents(e.slot.getItem());
        if (items == null) return;
        e.cancel();
        SimpleContainer container = new SimpleContainer(items.toArray(ItemStack[]::new));
        mc.setScreen(new PeekScreen(container, mc.player.getInventory(), e.slot.getItem().getHoverName(), e.screen));
    }

    @Subscribe
    private void onScreen(Events.ScreenInit e) {
        if (!buttons.get() || !(e.screen() instanceof ContainerScreen || e.screen() instanceof ShulkerBoxScreen)) return;
        if (e.screen() instanceof PeekScreen) return;
        var pos = (AbstractContainerScreenAccessor) e.screen();
        int x = pos.nocturnal$left() + pos.nocturnal$width() + 2, y = pos.nocturnal$top();
        Screens.getButtons(e.screen()).add(Button.builder(Component.literal("Steal"), b -> moveAll(true)).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        Screens.getButtons(e.screen()).add(Button.builder(Component.literal("Dump"), b -> moveAll(false)).bounds(x, y + BUTTON_HEIGHT + 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** Shift-clicks every stack out of the container, or every stack from your inventory into it. */
    private static void moveAll(boolean fromContainer) {
        AbstractContainerMenu menu = mc.player.containerMenu;
        for (Slot slot : menu.slots) {
            if (!slot.hasItem() || (slot.container instanceof Inventory) == fromContainer) continue;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    /** The 27 slots of a shulker box item, or null if it isn't one. */
    private static NonNullList<ItemStack> contents(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ShulkerBoxBlock)) return null;
        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    private record PreviewTooltip(NonNullList<ItemStack> items) implements ClientTooltipComponent {
        @Override
        public int getHeight(Font font) {
            return rows() * CELL + 4;
        }

        @Override
        public int getWidth(Font font) {
            return COLUMNS * CELL;
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            g.fill(x, y, x + COLUMNS * CELL, y + rows() * CELL, PREVIEW_BACKGROUND);
            for (int i = 0; i < items.size(); i++) {
                int sx = x + i % COLUMNS * CELL + 1, sy = y + i / COLUMNS * CELL + 1;
                g.renderItem(items.get(i), sx, sy);
                g.renderItemDecorations(font, items.get(i), sx, sy);
            }
        }

        private int rows() {
            return (items.size() + COLUMNS - 1) / COLUMNS;
        }
    }

    /** A chest screen over a copy of the shulker's items. Clicks do nothing and closing it goes back without telling the server. */
    private static final class PeekScreen extends ContainerScreen {
        private final Screen parent;

        PeekScreen(Container container, Inventory inventory, Component title, Screen parent) {
            super(ChestMenu.threeRows(0, inventory, container), inventory, title);
            this.parent = parent;
        }

        @Override
        protected void slotClicked(Slot slot, int index, int button, ClickType type) {}

        @Override
        public void onClose() {
            mc.setScreen(parent);
        }
    }
}
