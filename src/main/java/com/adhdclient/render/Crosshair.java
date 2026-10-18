package com.adhdclient.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Startpunkt für Tracer-Linien: ein Punkt kurz vor der Kamera, der genau im Fadenkreuz liegt.
 * Wird mit der echten Projektionsmatrix des Bildes (inkl. View Bobbing) zurückgerechnet, damit die
 * Linien beim Laufen nicht wackeln. Die Matrix setzt ein Mixin auf WorldRenderer.render.
 */
public final class Crosshair {

    private static final Matrix4f FRAME_PROJECTION = new Matrix4f();
    private static boolean hasFrameProjection;

    private Crosshair() {
    }

    public static void setFrameProjection(Matrix4f projection) {
        FRAME_PROJECTION.set(projection);
        hasFrameProjection = true;
    }

    public static Vec3d point(WorldExtractionContext ctx, Camera camera) {
        Vec3d cam = camera.getCameraPos();
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d fallback = cam.add(look.multiply(0.6));
        if (!hasFrameProjection) return fallback;
        Matrix4f inv = new Matrix4f(FRAME_PROJECTION).mul(ctx.viewMatrix()).invert();
        Vector4f a = inv.transform(new Vector4f(0, 0, -0.5f, 1));
        Vector4f b = inv.transform(new Vector4f(0, 0, 0.5f, 1));
        if (Math.abs(a.w) < 1e-9 || Math.abs(b.w) < 1e-9) return fallback;
        Vec3d pa = new Vec3d(a.x / a.w, a.y / a.w, a.z / a.w);
        Vec3d pb = new Vec3d(b.x / b.w, b.y / b.w, b.z / b.w);
        Vec3d dir = pb.subtract(pa);
        if (!Double.isFinite(dir.lengthSquared()) || dir.lengthSquared() < 1e-12) return fallback;
        dir = dir.normalize();
        if (dir.dotProduct(look) < 0) dir = dir.multiply(-1);
        Vec3d origin = pa.subtract(dir.multiply(pa.dotProduct(dir)));
        Vec3d p = origin.add(dir.multiply(0.6));
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z) || p.length() > 2) return fallback;
        return cam.add(p);
    }
}
