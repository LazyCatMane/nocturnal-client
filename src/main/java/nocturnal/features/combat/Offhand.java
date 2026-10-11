package nocturnal.features.combat;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.module.Modules;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;
import nocturnal.util.Combat;

import java.util.List;

/** Picks the offhand item for the situation. Replaces AutoTotem while on; the two turn each other off. */
public class Offhand extends Module {
    /** Swap-click button that targets the offhand slot. */
    private static final int OFFHAND_BUTTON = 40;
    /** Crystals farther than this can't hurt us. */
    private static final double CRYSTAL_THREAT_RANGE = 12;

    private static final List<Item> TOTEM = List.of(Items.TOTEM_OF_UNDYING);
    private static final List<Item> CRYSTAL = List.of(Items.END_CRYSTAL);
    private static final List<Item> GAPPLE = List.of(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);

    private final NumberSetting health = add(new NumberSetting("Health", "Always hold a totem at or below this health (including absorption)", 14, 1, 36, 1));
    private final BoolSetting lethalCrystals = add(new BoolSetting("Lethal crystals", "Hold a totem when a nearby crystal could kill you", true));
    private final BoolSetting crystal = add(new BoolSetting("Crystal", "Hold crystals while CrystalAura is on", true));
    private final BoolSetting gapple = add(new BoolSetting("Gapple", "Hold a golden apple while right-clicking with a sword", true));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait between swaps", 1, 0, 20, 1));
    private int cooldown;

    public Offhand() {
        super("Offhand", "Totem, crystal or gapple in your offhand depending on the situation", Category.COMBAT);
    }

    @Override
    public String info() {
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getOffhandItem();
        return held.isEmpty() ? null : held.getHoverName().getString();
    }

    @Override
    protected void onEnable() {
        Modules.get(AutoTotem.class).setEnabled(false);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (!inGame() || mc.gameMode == null || cooldown-- > 0 || mc.screen instanceof CreativeModeInventoryScreen) return;
        List<Item> want = wanted();
        if (holding(want)) return;
        if (!swapIn(want) && want != TOTEM && !holding(TOTEM)) swapIn(TOTEM);
    }

    private List<Item> wanted() {
        float hp = Combat.health(mc.player);
        if (hp <= health.get() || (lethalCrystals.get() && crystalThreat(hp))) return TOTEM;
        if (gapple.get() && mc.options.keyUse.isDown() && mc.player.getMainHandItem().is(ItemTags.SWORDS)) return GAPPLE;
        if (crystal.get() && Modules.get(CrystalAura.class).isEnabled()) return CRYSTAL;
        return TOTEM;
    }

    private boolean crystalThreat(float hp) {
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(CRYSTAL_THREAT_RANGE), e -> e instanceof EndCrystal)) {
            if (Combat.crystalDamage(mc.player, e.position()) >= hp) return true;
        }
        return false;
    }

    private boolean holding(List<Item> items) {
        return items.stream().anyMatch(mc.player.getOffhandItem()::is);
    }

    /** Swaps the first matching item (in list order) into the offhand. Returns false if none was found. */
    private boolean swapIn(List<Item> items) {
        AbstractContainerMenu menu = mc.player.containerMenu;
        for (Item item : items) {
            for (Slot slot : menu.slots) {
                if (slot.container == mc.player.getInventory() && slot.getItem().is(item)) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, OFFHAND_BUTTON, ClickType.SWAP, mc.player);
                    cooldown = delay.getInt();
                    return true;
                }
            }
        }
        return false;
    }
}
