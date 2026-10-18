package com.adhdclient.mixin;

import com.adhdclient.gui.HotKey;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Modul-Tasten auf der Maus: Drücken und Loslassen mitzählen. */
@Mixin(Mouse.class)
public abstract class HotKeyMouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void adhd$hotKey(long window, MouseInput input, int action, CallbackInfo ci) {
        HotKey.onInput(HotKey.mouse(input.button()), action);
    }
}
