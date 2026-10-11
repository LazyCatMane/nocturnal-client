package nocturnal.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = {"render", "tickRainParticles"}, at = @At("HEAD"), cancellable = true)
    private void nocturnal$noWeather(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.WEATHER)) ci.cancel();
    }
}
