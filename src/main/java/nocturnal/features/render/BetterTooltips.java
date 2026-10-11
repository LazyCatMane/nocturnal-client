package nocturnal.features.render;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;

import java.util.List;

/** Map pictures, the start of a book's first page, and exact durability in item tooltips. Shulkers are in ShulkerTools. */
public class BetterTooltips extends Module {
    private static final int MAP_SIZE = 96;
    private static final int BOOK_LINES = 4, BOOK_LINE_LENGTH = 40;

    private final BoolSetting maps = add(new BoolSetting("Maps", "Show the map picture", true));
    private final BoolSetting books = add(new BoolSetting("Books", "Show the start of the first page", true));
    private final BoolSetting durability = add(new BoolSetting("Durability", "Show durability left out of the maximum", true));

    private record MapImage(MapId id, MapItemSavedData data) implements TooltipComponent {}

    public BetterTooltips() {
        super("BetterTooltips", "Map, book and durability info in tooltips", Category.RENDER);
        TooltipComponentCallback.EVENT.register(data -> data instanceof MapImage m ? new MapTooltip(m.id, m.data) : null);
    }

    @Subscribe
    private void onImage(Events.TooltipImage e) {
        if (!maps.get() || mc.level == null) return;
        MapId id = e.stack.get(DataComponents.MAP_ID);
        if (id == null) return;
        MapItemSavedData data = MapItem.getSavedData(id, mc.level);
        if (data != null) e.image = new MapImage(id, data);
    }

    @Subscribe
    private void onLines(Events.TooltipLines e) {
        ItemStack stack = e.stack();
        if (books.get()) addBookPreview(stack, e.lines());
        if (durability.get() && stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            e.lines().add(Component.literal("Durability: " + (max - stack.getDamageValue()) + " / " + max).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void addBookPreview(ItemStack stack, List<Component> lines) {
        String page = null;
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null && !written.pages().isEmpty()) page = written.pages().getFirst().raw().getString();
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null && !writable.pages().isEmpty()) page = writable.pages().getFirst().raw();
        if (page == null || page.isBlank()) return;

        String[] pageLines = page.split("\n");
        for (int i = 0; i < Math.min(BOOK_LINES, pageLines.length); i++) {
            String text = pageLines[i].length() > BOOK_LINE_LENGTH ? pageLines[i].substring(0, BOOK_LINE_LENGTH) + "..." : pageLines[i];
            lines.add(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private record MapTooltip(MapId id, MapItemSavedData data) implements ClientTooltipComponent {
        @Override
        public int getHeight(Font font) {
            return MAP_SIZE + 2;
        }

        @Override
        public int getWidth(Font font) {
            return MAP_SIZE;
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            var texture = mc.getMapTextureManager().prepareMapTexture(id, data);
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        }
    }
}
