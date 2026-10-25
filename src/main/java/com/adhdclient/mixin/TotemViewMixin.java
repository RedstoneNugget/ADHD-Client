package com.adhdclient.mixin;

import com.adhdclient.totemview.TotemViewClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gegner-Totem-Anzeige: Namensschild anderer Spieler um den Totem-Hinweis ergänzen. */
@Mixin(EntityRenderer.class)
public abstract class TotemViewMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void adhd_client$totemLabel(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (state.displayName != null) state.displayName = TotemViewClient.decorate(entity, state.displayName);
    }
}
