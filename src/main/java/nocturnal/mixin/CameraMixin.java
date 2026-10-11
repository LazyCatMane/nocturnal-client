package nocturnal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean nocturnal$detached(boolean detached) {
        Events.CameraSetup e = Events.CameraSetup.INSTANCE;
        e.detached = detached;
        return Nocturnal.BUS.post(e).detached;
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void nocturnal$update(CallbackInfo ci, @Local(argsOnly = true) float tickDelta) {
        Events.CameraUpdate e = Events.CameraUpdate.INSTANCE;
        e.camera = (Camera) (Object) this;
        e.tickDelta = tickDelta;
        Nocturnal.BUS.post(e);
    }
}
