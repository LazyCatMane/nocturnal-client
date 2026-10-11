package nocturnal.features.misc;

import com.google.gson.JsonObject;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.StringSetting;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Shows Nocturnal on your Discord profile over Discord's local IPC pipe, no library needed.
 * Needs an application ID from discord.com/developers (the app's name is what Discord shows you playing).
 */
public class DiscordRpc extends Module {
    private static final int OP_HANDSHAKE = 0, OP_FRAME = 1;
    private static final long UPDATE_MS = 15_000;
    private static final int MAX_PIPES = 10;

    private final StringSetting appId = add(new StringSetting("App ID", "Application ID from discord.com/developers", ""));
    private final StringSetting image = add(new StringSetting("Image", "Asset key of an image uploaded to the app", "nocturnal"));
    private final BoolSetting showServer = add(new BoolSetting("Show server", "Show which server you're on", true));

    private volatile String details = "In menus", state = "Nocturnal Client";
    private final long start = System.currentTimeMillis() / 1000;
    private Thread thread;

    public DiscordRpc() {
        super("DiscordRpc", "Shows Nocturnal on your Discord profile", Category.MISC);
    }

    @Override
    protected void onEnable() {
        if (appId.get().isBlank()) {
            log("Set an App ID first: .discordrpc appid <id>");
            setEnabled(false);
            return;
        }
        thread = new Thread(this::run, "Nocturnal Discord RPC");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void onDisable() {
        if (thread != null) thread.interrupt();
        thread = null;
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        if (mc.player == null) {
            details = "In menus";
        } else if (mc.getCurrentServer() != null) {
            details = showServer.get() ? "Playing on " + mc.getCurrentServer().ip : "Playing multiplayer";
        } else {
            details = "Singleplayer";
        }
        state = mc.player == null ? "Nocturnal Client" : dimension();
    }

    private String dimension() {
        String path = mc.level.dimension().identifier().getPath();
        return switch (path) {
            case "overworld" -> "In the Overworld";
            case "the_nether" -> "In the Nether";
            case "the_end" -> "In the End";
            default -> "In " + path;
        };
    }

    /** Connects, then pushes the activity every 15 seconds. Reconnects quietly if Discord isn't running yet. */
    private void run() {
        Thread self = Thread.currentThread();
        while (thread == self) {
            try (Pipe pipe = Pipe.open()) {
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId.get().trim());
                pipe.write(OP_HANDSHAKE, hello);
                pipe.read();
                while (thread == self) {
                    pipe.write(OP_FRAME, activity());
                    pipe.read();
                    Thread.sleep(UPDATE_MS);
                }
            } catch (IOException e) {
                Nocturnal.LOG.debug("Discord RPC not connected: {}", e.getMessage());
            } catch (InterruptedException e) {
                return;
            }
            try {
                Thread.sleep(UPDATE_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private JsonObject activity() {
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", start);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", image.get());
        assets.addProperty("large_text", "Nocturnal Client");
        JsonObject activity = new JsonObject();
        activity.addProperty("details", details);
        activity.addProperty("state", state);
        activity.add("timestamps", timestamps);
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        return command;
    }

    /** Discord's IPC: a named pipe on Windows, a Unix socket elsewhere. Frames are opcode, length, then JSON. */
    private static final class Pipe implements Closeable {
        private final RandomAccessFile windows;
        private final SocketChannel unix;

        private Pipe(RandomAccessFile windows, SocketChannel unix) {
            this.windows = windows;
            this.unix = unix;
        }

        static Pipe open() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            for (int i = 0; i < MAX_PIPES; i++) {
                try {
                    if (isWindows) return new Pipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"), null);
                    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(Path.of(socketDir(), "discord-ipc-" + i)));
                    return new Pipe(null, channel);
                } catch (IOException ignored) {
                }
            }
            throw new IOException("Discord is not running");
        }

        private static String socketDir() {
            for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String dir = System.getenv(env);
                if (dir != null) return dir;
            }
            return "/tmp";
        }

        void write(int op, JsonObject json) throws IOException {
            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(op).putInt(body.length).put(body).flip();
            if (windows != null) windows.write(buf.array());
            else while (buf.hasRemaining()) unix.write(buf);
        }

        /** Reads and discards one reply so the pipe never fills up. */
        void read() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readFully(header);
            header.flip();
            header.getInt();
            readFully(ByteBuffer.allocate(header.getInt()));
        }

        private void readFully(ByteBuffer buf) throws IOException {
            if (windows != null) {
                windows.readFully(buf.array());
                buf.position(buf.limit());
                return;
            }
            while (buf.hasRemaining()) if (unix.read(buf) < 0) throw new IOException("Discord closed the pipe");
        }

        @Override
        public void close() throws IOException {
            if (windows != null) windows.close();
            if (unix != null) unix.close();
        }
    }
}
