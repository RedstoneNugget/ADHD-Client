package com.adhdclient.mixin;

import com.adhdclient.gui.HotKey;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Modul-Tasten: jedes Drücken zählen, auch wenn es kürzer als ein Tick ist. */
@Mixin(Keyboard.class)
public abstract class HotKeyKeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void adhd$hotKey(long window, int action, KeyInput input, CallbackInfo ci) {
        HotKey.onInput(input.key(), action);
    }
}
