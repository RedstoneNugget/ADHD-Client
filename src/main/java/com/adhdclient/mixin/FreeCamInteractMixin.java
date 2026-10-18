package com.adhdclient.mixin;

import com.adhdclient.freecam.FreeCamClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Free Cam: kein Angreifen, Abbauen, Benutzen oder Block-Picken aus der Kamera heraus. */
@Mixin(MinecraftClient.class)
public abstract class FreeCamInteractMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void adhd_client$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCamClient.enabled) cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void adhd_client$noUse(CallbackInfo ci) {
        if (FreeCamClient.enabled) ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void adhd_client$noBreaking(boolean breaking, CallbackInfo ci) {
        if (FreeCamClient.enabled) ci.cancel();
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void adhd_client$noPick(CallbackInfo ci) {
        if (FreeCamClient.enabled) ci.cancel();
    }
}
