package com.adhdclient.mixin;

import com.adhdclient.esp.EspClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP-Glow: ausgewählte Entities kommen in Minecrafts Umriss-Pass, als hätten sie den Glowing-Effekt. Nur clientseitig. */
@Mixin(Entity.class)
public abstract class EspGlowMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void adhd_client$espGlow(CallbackInfoReturnable<Boolean> cir) {
        if (EspClient.enabled && EspClient.config().glow && EspClient.isTarget((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
