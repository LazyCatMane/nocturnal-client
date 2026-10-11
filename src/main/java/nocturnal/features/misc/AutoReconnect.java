package nocturnal.features.misc;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.NumberSetting;

/** Adds a countdown button to the disconnect screen and rejoins the last server when it runs out. */
public class AutoReconnect extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds to wait before reconnecting", 30, 1, 300, 1));

    private ServerData lastServer;
    private Button button;
    private int ticksLeft;

    public AutoReconnect() {
        super("AutoReconnect", "Rejoins after being kicked", Category.MISC);
    }

    @Subscribe
    private void onScreen(Events.ScreenInit e) {
        if (!(e.screen() instanceof DisconnectedScreen screen) || lastServer == null) return;
        ticksLeft = delay.getInt() * 20;
        button = Button.builder(label(), b -> reconnect()).bounds(screen.width / 2 - 100, 8, 200, 20).build();
        Screens.getButtons(screen).add(button);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (mc.getCurrentServer() != null) lastServer = mc.getCurrentServer();
        if (!(mc.screen instanceof DisconnectedScreen) || button == null) return;
        if (--ticksLeft <= 0) reconnect();
        else button.setMessage(label());
    }

    private Component label() {
        return Component.literal("Reconnecting in " + (ticksLeft + 19) / 20 + "s (click to join now)");
    }

    private void reconnect() {
        button = null;
        ServerData server = lastServer;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
    }
}
