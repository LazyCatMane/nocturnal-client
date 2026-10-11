package nocturnal.mixin;

import net.minecraft.client.gui.Gui;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void nocturnal$noOverlay(CallbackInfo ci) {
        if (Events.Hide.hidden(Events.HideKind.OVERLAYS)) ci.cancel();
    }
}
