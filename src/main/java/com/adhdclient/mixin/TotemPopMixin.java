package com.adhdclient.mixin;

import com.adhdclient.totemview.TotemViewClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gegner-Totem-Anzeige: Totem-Pops (Entity-Status 35) mitzählen. */
@Mixin(LivingEntity.class)
public abstract class TotemPopMixin {

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void adhd_client$countPop(byte status, CallbackInfo ci) {
        if (status == 35 && ((LivingEntity) (Object) this).getEntityWorld().isClient()) TotemViewClient.onPop((LivingEntity) (Object) this);
    }
}
