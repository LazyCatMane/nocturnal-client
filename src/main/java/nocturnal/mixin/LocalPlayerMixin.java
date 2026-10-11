package nocturnal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void nocturnal$preMotion(CallbackInfo ci) {
        Nocturnal.BUS.post(Events.PreMotion.INSTANCE);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void nocturnal$postMotion(CallbackInfo ci) {
        Nocturnal.BUS.post(Events.PostMotion.INSTANCE);
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 nocturnal$move(Vec3 movement, @Local(argsOnly = true) MoverType type) {
        if (type != MoverType.SELF || !Nocturnal.BUS.hasListeners(Events.PlayerMove.class)) return movement;
        Events.PlayerMove e = Events.PlayerMove.INSTANCE;
        e.movement = movement;
        return Nocturnal.BUS.post(e).movement;
    }

    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void nocturnal$itemSlowdown(CallbackInfoReturnable<Boolean> cir) {
        if (Events.Slowdown.cancelled(Events.SlowdownKind.ITEM_USE)) cir.setReturnValue(false);
    }

    @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
    private void nocturnal$sneakSlowdown(CallbackInfoReturnable<Boolean> cir) {
        if (Events.Slowdown.cancelled(Events.SlowdownKind.SNEAK)) cir.setReturnValue(false);
    }
}
