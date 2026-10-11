package nocturnal.features.render;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Player nametags drawn on the HUD so they show through walls, sized like vanilla's. */
public class Nametags extends Module {
    /** Vanilla draws nametag text at this many blocks per font pixel. */
    private static final float VANILLA_SCALE = 0.025F;
    /** Vanilla puts the tag this far above the head. */
    private static final double ABOVE_HEAD = 0.5;
    private static final int BACKGROUND = 0x90000000, ITEM_SIZE = 16;
    private static final EquipmentSlot[] ITEM_ORDER = {
        EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    private final NumberSetting scale = add(new NumberSetting("Scale", "Size compared to vanilla nametags", 1, 0.5, 3, 0.1));
    private final BoolSetting items = add(new BoolSetting("Items", "Show armor and held items", true));
    private final BoolSetting durability = add(new BoolSetting("Durability", "Show durability percent above items", true));

    private final Matrix4f viewProjection = new Matrix4f();
    private float projectionScaleY;
    private boolean hasProjection;

    public Nametags() {
        super("Nametags", "Bigger player nametags with health and gear, through walls", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        hasProjection = false;
    }

    @Subscribe
    private void onProjection(Events.WorldProjection e) {
        Quaternionf view = mc.gameRenderer.getMainCamera().rotation().conjugate(new Quaternionf());
        viewProjection.set(e.projection).rotate(view);
        projectionScaleY = e.projection.m11();
        hasProjection = true;
    }

    @Subscribe
    private void onHide(Events.Hide e) {
        if (e.kind == Events.HideKind.PLAYER_NAMETAGS) e.cancel();
    }

    @Subscribe
    private void onRender(Events.Render2D e) {
        if (!inGame() || !hasProjection) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();

        List<Player> players = new ArrayList<>(mc.level.players());
        players.sort(Comparator.comparingDouble((Player p) -> p.distanceToSqr(cam)).reversed());
        for (Player p : players) {
            if (!p.isAlive() || (p == mc.player && !camera.isDetached())) continue;
            Vec3 rel = p.getPosition(e.tickDelta).add(0, p.getBbHeight() + ABOVE_HEAD, 0).subtract(cam);
            Vector4f clip = viewProjection.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1));
            if (clip.w <= 0.05F) continue;

            float x = (clip.x / clip.w + 1) / 2 * width;
            float y = (1 - clip.y / clip.w) / 2 * height;
            float pixelsPerBlock = projectionScaleY * height / 2 / clip.w;
            draw(e.graphics, p, x, y, VANILLA_SCALE * pixelsPerBlock * scale.get().floatValue());
        }
    }

    private void draw(GuiGraphics g, Player p, float x, float y, float s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);

        String name = p.getName().getString();
        float health = Combat.health(p);
        String hp = " " + Math.round(health);
        int textWidth = mc.font.width(name + hp);
        int left = -textWidth / 2;
        g.fill(left - 2, -11, left + textWidth + 2, 0, BACKGROUND);
        g.drawString(mc.font, name, left, -10, 0xFFFFFFFF, true);
        g.drawString(mc.font, hp, left + mc.font.width(name), -10, healthColor(health / p.getMaxHealth()), true);

        if (items.get()) drawItems(g, p);
        g.pose().popMatrix();
    }

    private void drawItems(GuiGraphics g, Player p) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : ITEM_ORDER) {
            ItemStack stack = p.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        int x = -stacks.size() * ITEM_SIZE / 2;
        int y = -12 - ITEM_SIZE;
        for (ItemStack stack : stacks) {
            g.renderItem(stack, x, y);
            g.renderItemDecorations(mc.font, stack, x, y);
            if (durability.get() && stack.isDamageableItem()) drawDurability(g, stack, x, y);
            x += ITEM_SIZE;
        }
    }

    private void drawDurability(GuiGraphics g, ItemStack stack, int x, int y) {
        float left = 1 - (float) stack.getDamageValue() / stack.getMaxDamage();
        String text = Math.round(left * 100) + "%";
        g.pose().pushMatrix();
        g.pose().translate(x + ITEM_SIZE / 2F, y - 4);
        g.pose().scale(0.5F, 0.5F);
        g.drawString(mc.font, text, -mc.font.width(text) / 2, 0, healthColor(left), true);
        g.pose().popMatrix();
    }

    /** Green when full, yellow at half, red when nearly gone. */
    private static int healthColor(float fraction) {
        if (fraction > 0.66F) return 0xFF55FF55;
        if (fraction > 0.33F) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
