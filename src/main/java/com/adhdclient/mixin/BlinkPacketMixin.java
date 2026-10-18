package com.adhdclient.mixin;

import com.adhdclient.blink.BlinkClient;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blink: Bewegungspakete zurückhalten, solange das Modul an ist. */
@Mixin(ClientConnection.class)
public abstract class BlinkPacketMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void adhd$blinkHold(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (BlinkClient.hold(packet)) ci.cancel();
    }
}
