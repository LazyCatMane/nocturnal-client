package nocturnal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /** Pushed far past any render distance, but kept finite so the fog shader never divides by zero. */
    private static final float FAR = 1.0E6F;

    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void nocturnal$noFog(CallbackInfoReturnable<Vector4f> cir, @Local FogData fog) {
        if (!Events.Hide.hidden(Events.HideKind.FOG)) return;
        fog.environmentalStart = fog.renderDistanceStart = FAR;
        fog.environmentalEnd = fog.renderDistanceEnd = FAR + 1;
    }
}
