package nocturnal.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void nocturnal$worldText(SubmitNodeCollection collection, MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        if (!Nocturnal.BUS.hasListeners(Events.WorldText.class) || collection.getTextSubmits().isEmpty()) return;
        Events.WorldText e = Events.WorldText.INSTANCE;
        e.submits = collection.getTextSubmits();
        Nocturnal.BUS.post(e);
        e.submits = null;
    }
}
