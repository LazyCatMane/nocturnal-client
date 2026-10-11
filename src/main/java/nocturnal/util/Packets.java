package nocturnal.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

import java.util.function.Consumer;

/** Builds and reads packets vanilla only exposes through private fields, by going through their wire format. */
public final class Packets {
    private static final int ATTACK_ACTION = 1;

    private Packets() {}

    /** Attack packet for an entity id, usable before the client has spawned that entity. */
    public static ServerboundInteractPacket attack(int entityId, boolean sneaking) {
        return decode(buf -> {
            buf.writeVarInt(entityId);
            buf.writeVarInt(ATTACK_ACTION);
            buf.writeBoolean(sneaking);
        });
    }

    /** The entity id this packet attacks, or -1 if it's an interaction instead of an attack. */
    public static int attackTarget(ServerboundInteractPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundInteractPacket.STREAM_CODEC.encode(buf, packet);
            int id = buf.readVarInt();
            return buf.readVarInt() == ATTACK_ACTION ? id : -1;
        } finally {
            buf.release();
        }
    }

    private static ServerboundInteractPacket decode(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            return ServerboundInteractPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }
}
