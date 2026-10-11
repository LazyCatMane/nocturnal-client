package nocturnal.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPosition")
    void nocturnal$setPosition(Vec3 position);

    @Invoker("setRotation")
    void nocturnal$setRotation(float yaw, float pitch);
}
