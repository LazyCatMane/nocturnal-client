package nocturnal.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nocturnal$hideParticle(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        ParticleType<?> type = options.getType();
        Events.HideKind kind = type == ParticleTypes.TOTEM_OF_UNDYING ? Events.HideKind.TOTEM
            : type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER ? Events.HideKind.EXPLOSIONS
            : null;
        if (kind != null && Events.Hide.hidden(kind)) cir.setReturnValue(null);
    }
}
