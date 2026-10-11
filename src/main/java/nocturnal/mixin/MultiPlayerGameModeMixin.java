package nocturnal.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"))
    private void nocturnal$mining(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Nocturnal.BUS.hasListeners(Events.Mining.class)) Nocturnal.BUS.post(new Events.Mining(pos));
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void nocturnal$attack(Player player, Entity target, CallbackInfo ci) {
        if (Nocturnal.BUS.hasListeners(Events.Attack.class)) Nocturnal.BUS.post(new Events.Attack(target));
    }
}
