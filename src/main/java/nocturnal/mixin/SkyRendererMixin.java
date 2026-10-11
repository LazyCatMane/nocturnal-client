package nocturnal.mixin;

import net.minecraft.client.renderer.SkyRenderer;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = {"renderSkyDisc", "renderEndSky"}, at = @At("HEAD"), cancellable = true)
    private void nocturnal$sky(CallbackInfo ci) {
        if (Events.SkyRender.cancelled(Events.SkyPart.SKY)) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void nocturnal$darkDisc(CallbackInfo ci) {
        if (Events.SkyRender.cancelled(Events.SkyPart.DARK_DISC)) ci.cancel();
    }

    @Inject(method = {"renderSunriseAndSunset", "renderSunMoonAndStars"}, at = @At("HEAD"), cancellable = true)
    private void nocturnal$celestials(CallbackInfo ci) {
        if (Events.SkyRender.cancelled(Events.SkyPart.CELESTIALS)) ci.cancel();
    }
}
