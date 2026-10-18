package com.adhdclient.mixin;

import com.adhdclient.render.Crosshair;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rein lesend: merkt sich die Projektionsmatrix, mit der die Welt in diesem Bild
 * tatsächlich gezeichnet wird. Sie enthält das Kamera-Wackeln beim Laufen (View Bobbing) und beim
 * Schaden – die Fabric API gibt nur die Matrix ohne Wackeln heraus. Damit beginnen Tracer-Linien
 * immer genau im Fadenkreuz.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void adhd_client$captureProjection(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                                               Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                               Matrix4f cullProjectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor,
                                               boolean renderSky, CallbackInfo ci) {
        Crosshair.setFrameProjection(projectionMatrix);
    }
}
