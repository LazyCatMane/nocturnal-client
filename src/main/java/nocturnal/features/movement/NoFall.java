package nocturnal.features.movement;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.mixin.MovePlayerPacketAccessor;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.NumberSetting;

public class NoFall extends Module {
    public enum Mode { PACKET, BUCKET }

    /** How far above the ground the predicted position packet puts you, well inside the server's reach. */
    private static final double PREDICT_HEIGHT = 1.5;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Packet claims you're on the ground, Bucket places water before landing", Mode.PACKET));
    private final NumberSetting minFall = add(new NumberSetting("Min fall", "Fall distance before NoFall acts", 3, 2, 20, 0.5));
    private final BoolSetting skipFlying = add(new BoolSetting("Skip flying", "Do nothing while using an elytra", true));
    private final BoolSetting predict = add(new BoolSetting("Predict", "Bucket: on fast falls that would skip the reach window, send a lower position first", true));

    /** Ticks to wait for the server to confirm a placement before trying again. */
    private static final int RETRY_TICKS = 4;

    /** Hotbar slot whose water bucket we emptied, or -1. */
    private int placedSlot = -1;
    private int retryCooldown;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.get().name();
    }

    @Override
    protected void onDisable() {
        placedSlot = -1;
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (mode.get() != Mode.PACKET || !(e.packet instanceof ServerboundMovePlayerPacket packet) || !shouldAct()) return;
        ((MovePlayerPacketAccessor) packet).nocturnal$setOnGround(true);
    }

    @Subscribe
    private void onTick(Events.TickPre e) {
        if (mode.get() != Mode.BUCKET || !inGame()) return;

        if (placedSlot >= 0 && (mc.player.onGround() || mc.player.isInWater())) {
            if (mc.player.getInventory().getItem(placedSlot).is(Items.BUCKET)) useLookingDown(placedSlot);
            placedSlot = -1;
            return;
        }
        if (!shouldAct() || mc.level.dimension() == Level.NETHER) return;

        if (retryCooldown-- > 0) return;
        int slot = hotbarSlot(Items.WATER_BUCKET);
        if (slot < 0) return;
        if (placedSlot >= 0 && mc.player.getInventory().getItem(placedSlot).is(Items.BUCKET)) return;

        double reach = mc.player.blockInteractionRange();
        double fallThisTick = -mc.player.getDeltaMovement().y;
        BlockHitResult ground = groundBelow(reach + Math.max(0, fallThisTick) + 1);
        if (ground.getType() != HitResult.Type.BLOCK) return;

        double eyeToGround = mc.player.getEyeY() - ground.getLocation().y;
        boolean inReach = eyeToGround <= reach;
        boolean wouldSkipWindow = predict.get() && eyeToGround - fallThisTick < mc.player.getEyeHeight();
        if (!inReach && !wouldSkipWindow) return;
        if (!inReach) {
            Vec3 p = mc.player.position();
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(p.x, ground.getLocation().y + PREDICT_HEIGHT, p.z, false, mc.player.horizontalCollision));
        }
        useLookingDown(slot);
        placedSlot = slot;
        retryCooldown = RETRY_TICKS;
    }

    private boolean shouldAct() {
        return mc.player != null && mc.player.fallDistance >= minFall.get() && !(skipFlying.get() && mc.player.isFallFlying());
    }

    private BlockHitResult groundBelow(double distance) {
        Vec3 eye = mc.player.getEyePosition();
        return mc.level.clip(new ClipContext(eye, eye.subtract(0, distance, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
    }

    /** Uses the item in the slot while looking straight down, server-side only: your view and held item don't change. */
    private void useLookingDown(int slot) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (slot != selected) mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), 90));
        if (slot != selected) mc.getConnection().send(new ServerboundSetCarriedItemPacket(selected));
    }

    private int hotbarSlot(Item item) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }
}
