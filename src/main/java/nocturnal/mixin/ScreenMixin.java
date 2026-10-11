package nocturnal.mixin;

import net.minecraft.client.gui.screens.Screen;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "isAllowedInPortal", at = @At("HEAD"), cancellable = true)
    private void nocturnal$allowInPortal(CallbackInfoReturnable<Boolean> cir) {
        if (Events.PortalScreen.cancelled()) cir.setReturnValue(true);
    }
}
