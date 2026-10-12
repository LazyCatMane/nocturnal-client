package nocturnal.mixin;

import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional; this class is missing when Baritone isn't installed. */
@Mixin(targets = "baritone.process.ElytraProcess", remap = false)
public abstract class ElytraProcessMixin {
    /** ElytraFly arrives on its own; a landing-spot repath would replace the flight path. */
    @Inject(method = "findSafeLandingSpot", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noLand(CallbackInfoReturnable<Object> cir) {
        if (Events.BaritoneFirework.cancelled()) cir.setReturnValue(null);
    }
}
