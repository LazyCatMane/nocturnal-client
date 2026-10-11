package nocturnal.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import nocturnal.Nocturnal;
import nocturnal.event.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void nocturnal$send(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!isClient() || !Nocturnal.BUS.hasListeners(Events.PacketSend.class)) return;
        if (Nocturnal.BUS.post(new Events.PacketSend(packet)).isCancelled()) ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void nocturnal$receive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!isClient() || !Nocturnal.BUS.hasListeners(Events.PacketReceive.class)) return;
        if (Nocturnal.BUS.post(new Events.PacketReceive(packet)).isCancelled()) ci.cancel();
    }

    /** Singleplayer runs a server in the same process; skip its side of the connection. */
    private boolean isClient() {
        return ((Connection) (Object) this).getReceiving() == PacketFlow.CLIENTBOUND;
    }
}
