package nocturnal.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag tag);

    @Unique private boolean nocturnal$readding;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), cancellable = true)
    private void nocturnal$receive(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (nocturnal$readding || !Nocturnal.BUS.hasListeners(Events.ChatReceive.class)) return;
        Events.ChatReceive e = Nocturnal.BUS.post(new Events.ChatReceive(message));
        if (e.isCancelled()) {
            ci.cancel();
        } else if (e.message != message) {
            ci.cancel();
            nocturnal$readding = true;
            try {
                addMessage(e.message, signature, tag);
            } finally {
                nocturnal$readding = false;
            }
        }
    }

    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100))
    private int nocturnal$historyLimit(int limit) {
        if (!Nocturnal.BUS.hasListeners(Events.ChatHistory.class)) return limit;
        Events.ChatHistory e = Events.ChatHistory.INSTANCE;
        e.limit = limit;
        return Nocturnal.BUS.post(e).limit;
    }
}
