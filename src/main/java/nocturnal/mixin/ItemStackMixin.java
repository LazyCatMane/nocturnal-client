package nocturnal.mixin;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void nocturnal$tooltipImage(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (!Nocturnal.BUS.hasListeners(Events.TooltipImage.class)) return;
        Events.TooltipImage e = Events.TooltipImage.INSTANCE;
        e.stack = (ItemStack) (Object) this;
        e.image = null;
        Nocturnal.BUS.post(e);
        if (e.image != null) cir.setReturnValue(Optional.of(e.image));
    }
}
