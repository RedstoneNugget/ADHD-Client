package com.adhdclient.mixin;

import com.adhdclient.butterfly.ButterflyRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Butterfly: zugeordnete Items in der ersten Person als Butterfly-Messer zeichnen. */
@Mixin(HeldItemRenderer.class)
public abstract class ButterflyHeldItemMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void adhd$butterfly(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress,
                                ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                CallbackInfo ci) {
        if (player.isUsingSpyglass()) return;
        if (ButterflyRenderer.render(player, hand, swingProgress, item, equipProgress, matrices, queue, light)) ci.cancel();
    }
}
