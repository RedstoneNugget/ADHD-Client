package com.adhdclient.mixin;

import com.adhdclient.autotool.AutoToolClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Auto Tool: vor dem Anschlagen eines Blocks das passende Werkzeug wählen. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class AutoToolMixin {

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void adhd_client$autoTool(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        AutoToolClient.onMine(pos);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
    private void adhd_client$autoToolUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        AutoToolClient.onMine(pos);
    }
}
