package com.adhdclient.basefinder.mixin;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.basefinder.scan.ScanManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Einziger (rein clientseitiger) Mixin: meldet Block-Änderungen im Client-World an den Scanner,
 * damit der betroffene Chunk gebündelt neu gescannt wird. Die Fabric API hat dafür kein Ereignis.
 *
 *  - handleBlockUpdate: Block-Updates und Sektions-Updates vom Server
 *  - setBlockState:     lokale Änderungen (z. B. vorhergesagtes Platzieren/Abbauen)
 *
 * Es wird nichts verändert oder gesendet – nur beobachtet.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void base_finder$onServerBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (!BaseFinderClient.enabled) return;
        ClientWorld self = (ClientWorld) (Object) this;
        ScanManager.INSTANCE.onBlockChanged(self, pos, self.getBlockState(pos), state);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void base_finder$onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!BaseFinderClient.enabled) return;
        ClientWorld self = (ClientWorld) (Object) this;
        ScanManager.INSTANCE.onBlockChanged(self, pos, self.getBlockState(pos), state);
    }
}
