package com.adhdclient.mixin;

import com.adhdclient.newchunks.NewChunksClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Neue Chunks: Block-Updates vom Server mitlesen (TAIL = schon auf dem Haupt-Thread). */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class NewChunksMixin {

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void adhd_client$block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        NewChunksClient.onBlockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void adhd_client$delta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        packet.visitUpdates(NewChunksClient::onBlockUpdate);
    }
}
