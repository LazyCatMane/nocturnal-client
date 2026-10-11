package nocturnal.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps the logged-in account at runtime, and reaches the right-click cooldown. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void nocturnal$setUser(User user);

    @Mutable
    @Accessor("userApiService")
    void nocturnal$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("profileKeyPairManager")
    void nocturnal$setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Accessor("rightClickDelay")
    int nocturnal$rightClickDelay();

    @Accessor("rightClickDelay")
    void nocturnal$setRightClickDelay(int ticks);
}
