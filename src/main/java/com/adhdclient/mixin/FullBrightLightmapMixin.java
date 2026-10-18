package com.adhdclient.mixin;

import com.adhdclient.fullbright.FullBrightClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Full Bright über die Licht-Tabelle. Reihenfolge der putFloat-Aufrufe in LightmapTextureManager.update (1.21.11):
 * 0 Umgebungslicht, 1 Himmelslicht, 2 Blocklicht, 3 Nachtsicht, 4 Dunkelheit, 5 Welt-Abdunklung (Boss), 6 Helligkeit.
 * Mit Full Bright: volles Himmelslicht wie am Tag, Nachtsicht in der eingestellten Stärke, keine Abdunklung,
 * maximale Helligkeit – und der Dunkelheits-Effekt optional weg.
 */
@Mixin(LightmapTextureManager.class)
public abstract class FullBrightLightmapMixin {

    private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";

    /** Himmelslicht wie mittags (nachts sonst gedämpft). */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 1))
    private float adhd_client$skyLight(float value) {
        return FullBrightClient.enabled ? Math.max(value, FullBrightClient.strength()) : value;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
    private float adhd_client$nightVision(float value) {
        return FullBrightClient.enabled ? Math.max(value, FullBrightClient.strength()) : value;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 4))
    private float adhd_client$darkness(float value) {
        return FullBrightClient.enabled && FullBrightClient.config().ignoreDarkness ? 0f : value;
    }

    /** Welt-Abdunklung (z. B. bei Boss-Kämpfen) aus. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 5))
    private float adhd_client$darkenWorld(float value) {
        return FullBrightClient.enabled ? 0f : value;
    }

    /** Helligkeit wie die Einstellung "Hell" (Gamma 100 %); der Dunkelheits-Anteil bleibt, wenn er nicht ignoriert wird. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 6))
    private float adhd_client$brightness(float value) {
        if (!FullBrightClient.enabled) return value;
        float gamma = MinecraftClient.getInstance().options.getGamma().getValue().floatValue();
        float bright = Math.max(gamma, FullBrightClient.strength());
        if (FullBrightClient.config().ignoreDarkness) return bright;
        float darkness = Math.max(0f, gamma - value);
        return Math.max(value, bright - darkness);
    }
}
