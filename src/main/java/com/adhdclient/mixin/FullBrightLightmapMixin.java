package com.adhdclient.mixin;

import com.adhdclient.fullbright.FullBrightClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Full Bright: Die Licht-Tabelle bekommt als 4. Wert die Nachtsicht-Stärke und als 5./7. die Dunkelheit
 * (Reihenfolge der putFloat-Aufrufe in LightmapTextureManager.update, 1.21.11). Mit Full Bright wird
 * Nachtsicht auf die eingestellte Stärke gesetzt und der Dunkelheits-Effekt optional entfernt.
 */
@Mixin(LightmapTextureManager.class)
public abstract class FullBrightLightmapMixin {

    private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
    private float adhd_client$nightVision(float value) {
        return FullBrightClient.enabled ? Math.max(value, FullBrightClient.strength()) : value;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 4))
    private float adhd_client$darkness(float value) {
        return FullBrightClient.enabled && FullBrightClient.config().ignoreDarkness ? 0f : value;
    }

    /** Helligkeit (Gamma) minus Dunkelheit – ohne Dunkelheit wieder die normale Gamma-Einstellung. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 6))
    private float adhd_client$gamma(float value) {
        if (!FullBrightClient.enabled || !FullBrightClient.config().ignoreDarkness) return value;
        return Math.max(value, MinecraftClient.getInstance().options.getGamma().getValue().floatValue());
    }
}
