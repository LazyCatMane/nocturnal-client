package nocturnal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
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

/** Runs for every entity, so each hook bails out early unless it's the local player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void nocturnal$turn(double yaw, double pitch, CallbackInfo ci) {
        if (!isLocalPlayer() || !Nocturnal.BUS.hasListeners(Events.Turn.class)) return;
        if (Nocturnal.BUS.post(new Events.Turn(yaw, pitch)).isCancelled()) ci.cancel();
    }

    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"), cancellable = true)
    private void nocturnal$blockSpeed(CallbackInfoReturnable<Float> cir) {
        if (isLocalPlayer() && Events.Slowdown.cancelled(Events.SlowdownKind.BLOCK_SPEED)) cir.setReturnValue(1.0F);
    }

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void nocturnal$stuck(CallbackInfo ci) {
        if (isLocalPlayer() && Events.Slowdown.cancelled(Events.SlowdownKind.STUCK_IN_BLOCK)) ci.cancel();
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 nocturnal$vehicleMove(Vec3 movement, @Local(argsOnly = true) MoverType type) {
        if (type != MoverType.SELF || !Nocturnal.BUS.hasListeners(Events.VehicleMove.class)) return movement;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() != (Object) this || player.getVehicle().getControllingPassenger() != player) return movement;
        Events.VehicleMove e = Events.VehicleMove.INSTANCE;
        e.vehicle = (Entity) (Object) this;
        e.movement = movement;
        return Nocturnal.BUS.post(e).movement;
    }

    private boolean isLocalPlayer() {
        return (Object) this == Minecraft.getInstance().player;
    }
}
