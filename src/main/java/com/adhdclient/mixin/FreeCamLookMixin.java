package com.adhdclient.mixin;

import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.freelook.FreeLookClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Cam / Free Look: Mausbewegung dreht die Kamera statt den Spieler. */
@Mixin(Entity.class)
public abstract class FreeCamLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void adhd_client$lookWithCamera(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        if (FreeCamClient.active) {
            FreeCamClient.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        } else if (FreeLookClient.active()) {
            FreeLookClient.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
