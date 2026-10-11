package nocturnal.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nocturnal$slotClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (hoveredSlot == null || !Nocturnal.BUS.hasListeners(Events.SlotClick.class)) return;
        var e = new Events.SlotClick((AbstractContainerScreen<?>) (Object) this, hoveredSlot, event.button());
        if (Nocturnal.BUS.post(e).isCancelled()) cir.setReturnValue(true);
    }
}
