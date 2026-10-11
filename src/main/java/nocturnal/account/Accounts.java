package nocturnal.account;

import com.google.gson.Gson;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import nocturnal.Nocturnal;
import nocturnal.config.Config;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.gui.AccountScreen;
import nocturnal.mixin.MinecraftAccessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Saved accounts, encrypted with Windows DPAPI so only the current Windows user can read them.
 * The last used account is restored on startup.
 */
public final class Accounts {
    private static final Path FILE = Config.DIR.resolve("accounts.dat");
    private static final Gson GSON = new Gson();

    private static final class Data {
        List<Account> accounts = new ArrayList<>();
        String active;
    }

    private static Data data = new Data();

    private Accounts() {}

    public static void init() {
        load();
        Nocturnal.BUS.register(new Accounts());
    }

    public static List<Account> all() {
        return data.accounts;
    }

    public static boolean isActive(Account account) {
        return account.uuid.equals(Minecraft.getInstance().getUser().getProfileId().toString());
    }

    public static void restoreLastUsed() {
        for (Account a : data.accounts) {
            if (a.uuid.equals(data.active)) use(a, error -> Nocturnal.LOG.warn("Could not restore account {}: {}", a.name, error));
        }
    }

    public static Account addCracked(String name) {
        Account a = Account.cracked(name, UUIDUtil.createOfflinePlayerUUID(name).toString());
        put(a);
        return a;
    }

    /** Stores a freshly logged-in Microsoft account and switches to it. */
    public static void addMicrosoft(MicrosoftAuth.Profile profile) {
        Account a = Account.microsoft(profile);
        put(a);
        apply(a, profile.minecraftToken());
    }

    public static void remove(Account account) {
        data.accounts.remove(account);
        save();
    }

    /** Switches to the account. Microsoft accounts refresh their token first, on a background thread. */
    public static void use(Account account, Consumer<String> onError) {
        if (account.type == Account.Type.CRACKED) {
            apply(account, "0");
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return MicrosoftAuth.refresh(account.refreshToken);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((profile, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                onError.accept(error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                return;
            }
            account.name = profile.name();
            account.refreshToken = profile.refreshToken();
            apply(account, profile.minecraftToken());
        }));
    }

    private static void put(Account account) {
        data.accounts.removeIf(a -> a.uuid.equals(account.uuid));
        data.accounts.add(account);
        save();
    }

    private static void apply(Account account, String token) {
        Minecraft mc = Minecraft.getInstance();
        boolean premium = account.type == Account.Type.MICROSOFT;
        User user = new User(account.name, UUID.fromString(account.uuid), token, Optional.empty(), Optional.empty());
        UserApiService api = UserApiService.OFFLINE;
        if (premium) {
            try {
                api = new YggdrasilAuthenticationService(mc.getProxy()).createUserApiService(token);
            } catch (RuntimeException e) {
                Nocturnal.LOG.warn("Could not create user API service, chat signing disabled", e);
            }
        }
        MinecraftAccessor access = (MinecraftAccessor) mc;
        access.nocturnal$setUser(user);
        access.nocturnal$setUserApiService(api);
        access.nocturnal$setProfileKeyPairManager(premium && api != UserApiService.OFFLINE
            ? ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath())
            : ProfileKeyPairManager.EMPTY_KEY_MANAGER);
        data.active = account.uuid;
        save();
    }

    @Subscribe
    private void onScreen(Events.ScreenInit e) {
        boolean title = e.screen() instanceof TitleScreen;
        if (!title && !(e.screen() instanceof JoinMultiplayerScreen)) return;
        int width = 130;
        int x = title ? 4 : e.screen().width - width - 4;
        String name = Minecraft.getInstance().getUser().getName();
        Screens.getButtons(e.screen()).add(Button.builder(Component.literal("Account: " + name),
            b -> Minecraft.getInstance().setScreen(new AccountScreen(e.screen()))).bounds(x, 4, width, 20).build());
    }

    private static void load() {
        if (!Files.exists(FILE)) return;
        try {
            byte[] bytes = Files.readAllBytes(FILE);
            if (Platform.isWindows()) bytes = Crypt32Util.cryptUnprotectData(bytes);
            Data loaded = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Data.class);
            if (loaded != null && loaded.accounts != null) data = loaded;
        } catch (Exception e) {
            Nocturnal.LOG.error("Could not read saved accounts", e);
        }
    }

    private static void save() {
        try {
            byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            if (Platform.isWindows()) bytes = Crypt32Util.cryptProtectData(bytes);
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, bytes);
        } catch (Exception e) {
            Nocturnal.LOG.error("Could not save accounts", e);
        }
    }
}
