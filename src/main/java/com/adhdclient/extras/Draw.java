package com.adhdclient.extras;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

/** Gemeinsame Zeichenhilfen für die kleineren Render-Module (Gizmos, durch Wände sichtbar). */
public final class Draw {

    private Draw() {
    }

    /** Schriftzug mit dunklem Hintergrund, zur Kamera gedreht und mit der Entfernung mitwachsend. */
    public static void label(String text, Vec3d anchor, int color, Camera camera, float scaleMul) {
        Vec3d cam = camera.getCameraPos();
        double dist = anchor.distanceTo(cam);
        float scale = (float) (0.5f * scaleMul * MathHelper.clamp(dist / 8.0, 1.0, 16.0));
        double px = scale / 16.0;
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();
        int w = MinecraftClient.getInstance().textRenderer.getWidth(text);
        double halfW = (w / 2.0 + 3) * px;
        Vec3d back = anchor.add(forward.multiply(0.02 + dist * 0.004));
        Vec3d a = back.add(right.multiply(-halfW)).add(up.multiply(-10 * px));
        Vec3d b = back.add(right.multiply(halfW)).add(up.multiply(-10 * px));
        Vec3d c = back.add(right.multiply(halfW)).add(up.multiply(2 * px));
        Vec3d d = back.add(right.multiply(-halfW)).add(up.multiply(2 * px));
        GizmoDrawing.quad(a, b, c, d, DrawStyle.filled(0x90000000)).ignoreOcclusion();
        // Style.left() ist in den Yarn-Namen die zentrierte Variante
        GizmoDrawing.text(text, anchor, TextGizmo.Style.left(0xFF000000 | color).scaled(scale)).ignoreOcclusion();
    }
}
