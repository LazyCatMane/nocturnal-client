package nocturnal.mixin;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private <V> void nocturnal$value(EnvironmentAttribute<V> attribute, float partialTick, CallbackInfoReturnable<V> cir) {
        if (!Nocturnal.BUS.hasListeners(Events.EnvironmentValue.class)) return;
        Events.EnvironmentValue e = Events.EnvironmentValue.INSTANCE;
        e.attribute = attribute;
        e.value = null;
        Nocturnal.BUS.post(e);
        if (e.value != null) cir.setReturnValue((V) e.value);
    }
}
