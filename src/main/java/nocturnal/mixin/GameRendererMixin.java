package nocturnal.mixin;

import net.minecraft.client.renderer.GameRenderer;
import nocturnal.event.Events;
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
}
