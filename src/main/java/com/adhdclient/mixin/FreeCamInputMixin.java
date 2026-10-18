package com.adhdclient.mixin;

import com.adhdclient.freecam.FreeCamClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Cam: der Spieler bekommt keine Lauf-Eingaben, solange die Kamera frei fliegt. */
@Mixin(KeyboardInput.class)
public abstract class FreeCamInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void adhd_client$freezePlayer(CallbackInfo ci) {
        if (!FreeCamClient.active) return;
        this.playerInput = PlayerInput.DEFAULT;
        this.movementVector = Vec2f.ZERO;
    }
}
