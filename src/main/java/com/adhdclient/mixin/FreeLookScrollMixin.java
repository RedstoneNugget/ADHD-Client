package com.adhdclient.mixin;

import com.adhdclient.freelook.FreeLookClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Look: Mausrad ändert den Kamera-Abstand statt den Hotbar-Slot, solange umgeschaut wird. */
@Mixin(Mouse.class)
public abstract class FreeLookScrollMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void adhd_client$zoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        if (FreeLookClient.scroll(vertical)) ci.cancel();
    }
}
