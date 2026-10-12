package nocturnal.mixin;

import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baritone's vanilla elytra flyer. Optional; this class is missing when Baritone isn't installed.
 * Loom remaps Baritone's private method names, so tickUseFireworks is {@code a(Vec3, Vec3, boolean, boolean)}.
 */
@Mixin(targets = "baritone.process.elytra.ElytraBehavior", remap = false)
public abstract class ElytraBehaviorMixin {
    @Inject(method = "a(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;ZZ)V", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noFireworks(CallbackInfo ci) {
        if (Events.BaritoneFirework.cancelled()) ci.cancel();
    }
}
