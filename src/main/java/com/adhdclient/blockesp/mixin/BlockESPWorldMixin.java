package com.adhdclient.blockesp.mixin;

import com.adhdclient.blockesp.BlockESPClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Meldet Block-Änderungen an Block ESP, damit der Chunk neu durchsucht wird. Nur beobachtend. */
@Mixin(ClientWorld.class)
public abstract class BlockESPWorldMixin {

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void block_esp$onServerBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        BlockESPClient.scanner().onBlockChanged(pos);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("TAIL"))
    private void block_esp$onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        BlockESPClient.scanner().onBlockChanged(pos);
    }
}
