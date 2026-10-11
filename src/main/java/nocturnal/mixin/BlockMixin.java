package nocturnal.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void nocturnal$renderAllFaces(BlockState state, BlockState neighbor, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Nocturnal.BUS.hasListeners(Events.BlockVisibility.class) && !Events.BlockVisibility.hidden(state)) cir.setReturnValue(true);
    }
}
