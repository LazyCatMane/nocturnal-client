package nocturnal.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void nocturnal$hideEntity(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        Events.HideKind kind = entity instanceof ItemEntity ? Events.HideKind.ITEMS
            : entity instanceof FallingBlockEntity ? Events.HideKind.FALLING_BLOCKS
            : entity instanceof ArmorStand ? Events.HideKind.ARMOR_STANDS
            : entity instanceof ItemFrame ? Events.HideKind.ITEM_FRAMES
            : entity instanceof WitherSkull || entity instanceof Fireball || entity instanceof DragonFireball ? Events.HideKind.FIREBALLS
            : null;
        if (kind != null && Events.Hide.hidden(kind)) cir.setReturnValue(false);
    }
}
