package nocturnal.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape nocturnal$hideBlock(BlockState state, Operation<RenderShape> original) {
        return Events.BlockVisibility.hidden(state) ? RenderShape.INVISIBLE : original.call(state);
    }

    @WrapWithCondition(method = "compile", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
    private boolean nocturnal$hideLiquid(BlockRenderDispatcher dispatcher, BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState state, FluidState fluid) {
        return !Events.BlockVisibility.hidden(state);
    }
}
