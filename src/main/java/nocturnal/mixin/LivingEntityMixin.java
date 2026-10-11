package nocturnal.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void nocturnal$standOnFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && Events.StandOnFluid.cancelled(fluid)) cir.setReturnValue(true);
    }
}
