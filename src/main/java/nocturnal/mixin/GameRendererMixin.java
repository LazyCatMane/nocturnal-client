package nocturnal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.GameRenderer;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noHurtCam(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.HURT_CAM)) ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V"))
    private void nocturnal$projection(CallbackInfo ci, @Local(ordinal = 0) Matrix4f projection) {
        if (!Nocturnal.BUS.hasListeners(Events.WorldProjection.class)) return;
        Events.WorldProjection e = Events.WorldProjection.INSTANCE;
        e.projection = projection;
        Nocturnal.BUS.post(e);
    }
}
