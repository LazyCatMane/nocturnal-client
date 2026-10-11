package nocturnal.features.research;

import net.minecraft.network.protocol.Packet;
import nocturnal.event.Events;
import nocturnal.event.Subscribe;
import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.EnumSetting;
import nocturnal.setting.StringSetting;
import nocturnal.util.Chat;
import nocturnal.util.LogFile;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/** Logs packets by their protocol name (e.g. move_player_pos), filtered by include/exclude lists. */
public class PacketLogger extends Module {
    public enum Direction { BOTH, SENT, RECEIVED }
    public enum Output { CHAT, FILE, BOTH }

    private final EnumSetting<Direction> direction = add(new EnumSetting<>("Direction", "Which packets to log", Direction.BOTH));
    private final EnumSetting<Output> output = add(new EnumSetting<>("Output", "Chat, nocturnal/research/packets.log, or both", Output.CHAT));
    private final StringSetting include = add(new StringSetting("Include", "Comma-separated name parts to log. Empty logs everything", ""));
    private final StringSetting exclude = add(new StringSetting("Exclude", "Comma-separated name parts to skip",
        "keep_alive,bundle,set_time,move_entity,rotate_head,set_entity_motion,entity_position_sync,level_chunk,light_update,chunk_batch,block_update,section_blocks_update,forget_level_chunk,client_tick_end"));

    private final LogFile file = new LogFile("research/packets.log", null);
    private String parsedInclude, parsedExclude;
    private List<String> includeList = List.of(), excludeList = List.of();

    public PacketLogger() {
        super("PacketLogger", "Logs sent and received packets", Category.RESEARCH);
    }

    @Override
    protected void onEnable() {
        file.open();
    }

    @Override
    protected void onDisable() {
        file.close();
    }

    @Subscribe
    private void onSend(Events.PacketSend e) {
        if (direction.get() != Direction.RECEIVED) log("-> ", e.packet);
    }

    @Subscribe
    private void onReceive(Events.PacketReceive e) {
        if (direction.get() != Direction.SENT) log("<- ", e.packet);
    }

    @Subscribe
    private void onTick(Events.TickPost e) {
        file.flush();
    }

    private void log(String arrow, Packet<?> packet) {
        String name = packet.type().id().getPath();
        if (!matches(name)) return;
        String line = arrow + name;
        if (output.get() != Output.FILE) Chat.info(line);
        if (output.get() != Output.CHAT) file.write(LocalTime.now() + " " + line + " " + packet);
    }

    private boolean matches(String name) {
        refreshFilters();
        for (String part : excludeList) if (name.contains(part)) return false;
        if (includeList.isEmpty()) return true;
        for (String part : includeList) if (name.contains(part)) return true;
        return false;
    }

    private void refreshFilters() {
        if (include.get() != parsedInclude) {
            parsedInclude = include.get();
            includeList = split(parsedInclude);
        }
        if (exclude.get() != parsedExclude) {
            parsedExclude = exclude.get();
            excludeList = split(parsedExclude);
        }
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
