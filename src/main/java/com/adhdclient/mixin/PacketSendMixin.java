package com.adhdclient.mixin;

import com.adhdclient.blink.BlinkClient;
import com.adhdclient.packettools.PacketLog;
import com.adhdclient.packettools.PacketToolsClient;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ausgehende Pakete: Blink hält Bewegungen zurück, Packet Tools hält an oder sammelt Klicks; Gesendetes geht ins Paket-Log. */
@Mixin(ClientConnection.class)
public abstract class PacketSendMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void adhd$holdPackets(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        // Nur die eigene Verbindung zum Server – im Einzelspieler nutzt der eingebaute Server dieselbe Klasse
        ClientPlayNetworkHandler own = MinecraftClient.getInstance().getNetworkHandler();
        if (own == null || own.getConnection() != (Object) this) return;
        if (BlinkClient.hold(packet) || PacketToolsClient.intercept(packet)) ci.cancel();
        else PacketLog.record(packet);
    }
}
