package com.adhdclient.mixin;

import com.adhdclient.fullbright.FullBrightClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Full Bright "Himmel wie am Tag": Die Tageszeit der Client-Welt wird als Mittag gemeldet. Himmel, Sonne, Nebel
 * und Himmelslicht richten sich danach; die echte Zeit vom Server läuft unverändert weiter.
 */
@Mixin(targets = "net.minecraft.client.world.ClientWorld$Properties")
public abstract class FullBrightDayMixin {

    private static final long NOON = 6000L;

    @Shadow
    private long timeOfDay;

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void adhd_client$noon(CallbackInfoReturnable<Long> cir) {
        if (FullBrightClient.enabled && FullBrightClient.config().dayTime) {
            cir.setReturnValue(timeOfDay - Math.floorMod(timeOfDay, 24000L) + NOON);
        }
    }
}
