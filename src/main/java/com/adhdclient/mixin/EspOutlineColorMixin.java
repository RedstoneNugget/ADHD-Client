package com.adhdclient.mixin;

import com.adhdclient.esp.EspClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Färbt den ESP-Glow-Umriss wie die Box (Entfernung, Team oder feste Farbe). */
@Mixin(EntityRenderer.class)
public abstract class EspOutlineColorMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void adhd_client$espOutlineColor(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state.outlineColor == 0 || !EspClient.enabled || !EspClient.config().glow) return;
        if (!EspClient.isTarget(entity)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        double dist = mc.player == null ? 0 : entity.distanceTo(mc.player);
        state.outlineColor = ColorHelper.fullAlpha(EspClient.colorFor(entity, dist));
    }
}
