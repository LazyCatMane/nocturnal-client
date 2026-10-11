package nocturnal.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void nocturnal$hideNametag(CallbackInfoReturnable<Boolean> cir) {
        if (Events.Hide.hidden(Events.HideKind.PLAYER_NAMETAGS)) cir.setReturnValue(false);
    }
}
