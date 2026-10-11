package nocturnal.gui;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nocturnal.account.Account;
import nocturnal.account.Accounts;
import nocturnal.account.MicrosoftAuth;
import nocturnal.features.render.ClickGui;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AccountScreen extends Screen {
    private static final int ROWS = 8;

    private final Screen parent;
    private EditBox nameBox;
    private UUID shownUser;
    private volatile String status = "";
    private volatile boolean loggingIn;
    private int page;

    public AccountScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shownUser = minecraft.getUser().getProfileId();
        int cx = width / 2;
        int y = 30;

        List<Account> accounts = Accounts.all();
        page = Math.max(0, Math.min(page, (accounts.size() - 1) / ROWS));
        for (int i = page * ROWS; i < Math.min(accounts.size(), (page + 1) * ROWS); i++) {
            Account a = accounts.get(i);
            String label = (Accounts.isActive(a) ? "> " : "") + a.name + (a.type == Account.Type.CRACKED ? " (cracked)" : " (premium)");
            addRenderableWidget(Button.builder(Component.literal(label), b -> use(a)).bounds(cx - 110, y, 190, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                Accounts.remove(a);
                rebuildWidgets();
            }).bounds(cx + 84, y, 26, 20).build());
            y += 22;
        }
        if (accounts.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(cx - 110, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(cx + 90, y, 20, 20).build());
        }

        y = height - 76;
        nameBox = new EditBox(font, cx - 110, y, 150, 20, Component.literal("Username"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Cracked username"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Add cracked"), b -> addCracked()).bounds(cx + 44, y, 66, 20).build());

        Button microsoft = Button.builder(Component.literal("Add Microsoft account"), b -> addMicrosoft()).bounds(cx - 110, y + 24, 220, 20).build();
        microsoft.active = !loggingIn;
        addRenderableWidget(microsoft);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(cx - 110, y + 48, 220, 20).build());
    }

    @Override
    public void tick() {
        if (!minecraft.getUser().getProfileId().equals(shownUser)) rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, "Accounts - logged in as " + minecraft.getUser().getName(), width / 2, 12, ClickGui.accent());
        g.drawCenteredString(font, status, width / 2, height - 92, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void use(Account account) {
        status = "Switching to " + account.name + "...";
        Accounts.use(account, error -> status = "Error: " + error);
    }

    private void addCracked() {
        String name = nameBox.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            status = "Names are 3-16 letters, numbers or _";
            return;
        }
        use(Accounts.addCracked(name));
    }

    private void addMicrosoft() {
        loggingIn = true;
        status = "Requesting login code...";
        rebuildWidgets();
        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode();
                minecraft.execute(() -> {
                    minecraft.keyboardHandler.setClipboard(code.userCode());
                    Util.getPlatform().openUri(code.verificationUri());
                    status = "Enter code " + code.userCode() + " in your browser (copied to clipboard)";
                });
                MicrosoftAuth.Profile profile = MicrosoftAuth.awaitDeviceLogin(code);
                minecraft.execute(() -> {
                    Accounts.addMicrosoft(profile);
                    status = "Logged in as " + profile.name();
                    finishLogin();
                });
            } catch (Exception e) {
                minecraft.execute(() -> {
                    status = "Error: " + e.getMessage();
                    finishLogin();
                });
            }
        });
    }

    private void finishLogin() {
        loggingIn = false;
        if (minecraft.screen == this) rebuildWidgets();
    }
}
