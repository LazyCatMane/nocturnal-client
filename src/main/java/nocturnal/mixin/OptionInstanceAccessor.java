package nocturnal.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option without range validation, e.g. gamma above 1.0. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    void nocturnal$setValue(Object value);
}
