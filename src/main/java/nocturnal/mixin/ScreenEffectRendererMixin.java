package nocturnal.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noTotem(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.TOTEM)) ci.cancel();
    }

    @Inject(method = {"renderFire", "renderWater", "renderTex"}, at = @At("HEAD"), cancellable = true)
    private static void nocturnal$noOverlay(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.OVERLAYS)) ci.cancel();
    }
}
