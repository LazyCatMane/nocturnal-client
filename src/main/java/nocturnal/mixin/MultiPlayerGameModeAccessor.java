package nocturnal.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    /** Tells the server which hotbar slot is selected, if it changed since the last time. */
    @Invoker("ensureHasSentCarriedItem")
    void nocturnal$syncSelectedSlot();
}
