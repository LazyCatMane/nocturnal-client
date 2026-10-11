package nocturnal.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos") int nocturnal$left();
    @Accessor("topPos") int nocturnal$top();
    @Accessor("imageWidth") int nocturnal$width();
}
