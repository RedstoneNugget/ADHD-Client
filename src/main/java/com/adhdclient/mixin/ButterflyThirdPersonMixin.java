package com.adhdclient.mixin;

import com.adhdclient.butterfly.ButterflyClient;
import com.adhdclient.butterfly.ButterflyRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Butterfly in der dritten Person (F5): beim eigenen Spieler das Messer statt des Items in die Hand. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class ButterflyThirdPersonMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void adhd$butterfly(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices,
                                OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (stack.isEmpty() || ButterflyClient.lookFor(stack) == null || !ButterflyRenderer.isOwnPlayer(state)) return;
        Object model = ((FeatureRenderer) (Object) this).getContextModel();
        if (!(model instanceof ModelWithArms arms)) return;
        matrices.push();
        arms.setArmAngle(state, arm, matrices);
        ButterflyRenderer.renderThirdPerson(state, arm, matrices, queue, light);
        matrices.pop();
        ci.cancel();
    }
}
