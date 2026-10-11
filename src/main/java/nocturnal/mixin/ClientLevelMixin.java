package nocturnal.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "trackExplosionEffects", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noExplosionDebris(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.EXPLOSIONS)) ci.cancel();
    }
}
