package com.adhdclient.mixin;

import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.freelook.FreeLookClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nach Minecrafts eigener Kamera-Berechnung:
 * <ul>
 *   <li>Free Cam: Position und Blickrichtung kommen komplett von Free Cam. Die Kamera gilt als "dritte Person",
 *       damit Minecraft den eigenen Körper zeichnet.</li>
 *   <li>Free Look: in der dritten Person Drehung (beim Umschauen) und Abstand ersetzen – mit oder ohne Stopp vor Blöcken.</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class FreeLookCameraMixin {

    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void adhd_client$freeCamera(World area, Entity focused, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (focused == null) return;
        if (FreeCamClient.enabled) {
            setRotation(FreeCamClient.yaw(), FreeCamClient.pitch());
            setPos(FreeCamClient.cameraPos(tickProgress));
            this.thirdPerson = true;
            return;
        }
        if (!thirdPerson || !FreeLookClient.enabled) return;
        boolean orbit = FreeLookClient.active();
        if (!orbit && !FreeLookClient.config().applyToF5) return;
        if (orbit) setRotation(FreeLookClient.yaw(), FreeLookClient.pitch());
        Vec3d eye = focused.getCameraPosVec(tickProgress);
        Vec3d back = Vec3d.fromPolar(getPitch(), getYaw()).multiply(-1);
        setPos(eye.add(back.multiply(FreeLookClient.clippedDistance(focused, eye, back))));
    }
}
