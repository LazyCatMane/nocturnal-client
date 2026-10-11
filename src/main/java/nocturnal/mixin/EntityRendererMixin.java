package nocturnal.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void nocturnal$outline(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        if (!Nocturnal.BUS.hasListeners(Events.EntityOutline.class)) return;
        Events.EntityOutline e = Events.EntityOutline.INSTANCE;
        e.entity = entity;
        e.color = 0;
        Nocturnal.BUS.post(e);
        if (e.color != 0) cir.getReturnValue().outlineColor = ARGB.opaque(e.color);
    }
}
