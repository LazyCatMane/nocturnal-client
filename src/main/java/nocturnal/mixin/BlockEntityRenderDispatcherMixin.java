package nocturnal.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void nocturnal$hideBlockEntity(BlockEntity blockEntity, float tickDelta, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfoReturnable<BlockEntityRenderState> cir) {
        Events.HideKind kind = blockEntity instanceof BeaconBlockEntity ? Events.HideKind.BEACON_BEAMS
            : blockEntity instanceof BannerBlockEntity ? Events.HideKind.BANNERS
            : blockEntity instanceof EnchantingTableBlockEntity ? Events.HideKind.ENCHANTING_BOOK
            : null;
        if (kind != null && Events.Hide.hidden(kind)) cir.setReturnValue(null);
    }
}
