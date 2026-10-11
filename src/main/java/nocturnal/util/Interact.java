package nocturnal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.mixin.MultiPlayerGameModeAccessor;

import java.util.List;
import java.util.function.Predicate;

/** Using items on blocks and moving items into your hand, shared by the modules that place things. */
public final class Interact {
    private static final Minecraft mc = Minecraft.getInstance();

    /** How a module may reach an item that isn't already in hand. */
    public enum Switch { NONE, NORMAL, SILENT }

    private Interact() {}

    public static int hotbarSlot(Predicate<ItemStack> item) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (item.test(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /** First inventory index (hotbar 0-8, then main 9-35) holding the item, or -1. */
    public static int inventorySlot(Predicate<ItemStack> item) {
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (item.test(inventory.getItem(i))) return i;
        }
        return -1;
    }

    /** The hand to use the item with, or null if it can't be reached with this switch mode. */
    public static InteractionHand handFor(Predicate<ItemStack> item, Switch mode) {
        if (item.test(mc.player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (item.test(mc.player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return mode != Switch.NONE && hotbarSlot(item) >= 0 ? InteractionHand.MAIN_HAND : null;
    }

    /**
     * Uses the item on each hit through the normal game path, so the server, item count and slot stay in sync.
     * Switches once for all hits. Returns false if the item couldn't be reached.
     */
    public static boolean useOn(Predicate<ItemStack> item, List<BlockHitResult> hits, Switch mode) {
        InteractionHand hand = handFor(item, mode);
        if (hand == null || hits.isEmpty()) return false;

        Inventory inventory = mc.player.getInventory();
        int restore = -1;
        if (hand == InteractionHand.MAIN_HAND && !item.test(mc.player.getMainHandItem())) {
            if (mode == Switch.SILENT) restore = inventory.getSelectedSlot();
            inventory.setSelectedSlot(hotbarSlot(item));
        }
        for (BlockHitResult target : hits) {
            mc.gameMode.useItemOn(mc.player, hand, target);
            mc.player.swing(hand);
        }
        if (restore >= 0) {
            inventory.setSelectedSlot(restore);
            ((MultiPlayerGameModeAccessor) mc.gameMode).nocturnal$syncSelectedSlot();
        }
        return true;
    }

    /** True if nothing blocks our eyes from the point. When aiming at a block's face, hitting that block counts. */
    public static boolean canSee(Vec3 point, BlockPos block) {
        BlockHitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS || (block != null && result.getBlockPos().equals(block));
    }

    /** A reachable face of a solid neighbour to place a block at pos against, or null. Hidden faces need wallsRange. */
    public static BlockHitResult placeAgainst(BlockPos pos, double range, double wallsRange) {
        Vec3 eye = mc.player.getEyePosition();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (mc.level.getBlockState(neighbor).getCollisionShape(mc.level, neighbor).isEmpty()) continue;
            Direction face = dir.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            double reach = hitVec.distanceTo(eye);
            if (reach > range || (reach > wallsRange && !canSee(hitVec, neighbor))) continue;
            return new BlockHitResult(hitVec, face, neighbor, false);
        }
        return null;
    }

    /**
     * Swaps an inventory index (9-35) with a hotbar slot through the player inventory screen's slots.
     * Only works while no other container is open. Calling it again with the same arguments swaps back.
     */
    public static boolean swapWithHotbar(int inventoryIndex, int hotbarSlot) {
        if (mc.player.containerMenu != mc.player.inventoryMenu) return false;
        int menuSlot = inventoryIndex < Inventory.getSelectionSize() ? InventoryMenu.USE_ROW_SLOT_START + inventoryIndex : inventoryIndex;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, hotbarSlot, ClickType.SWAP, mc.player);
        return true;
    }
}
