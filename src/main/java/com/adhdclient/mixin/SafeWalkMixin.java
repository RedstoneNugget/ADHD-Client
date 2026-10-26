package com.adhdclient.mixin;

import com.adhdclient.safewalk.SafeWalkClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Safe Walk: an Kanten wie beim Schleichen anhalten. */
@Mixin(PlayerEntity.class)
public abstract class SafeWalkMixin {

    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void adhd_client$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (SafeWalkClient.active((PlayerEntity) (Object) this)) cir.setReturnValue(true);
    }
}
