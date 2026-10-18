package com.adhdclient.esp;

import com.adhdclient.render.Crosshair;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import java.util.Locale;

/**
 * Zeichnet Box, Tracer und Namensschild für die ausgewählten Entities über das Gizmo-System von 1.21.11
 * (mit ignoreOcclusion() durch Wände sichtbar). Der leuchtende Umriss läuft über eigene Mixins.
 */
public final class EspRenderer {

    private static final float BASE_TEXT_SCALE = 0.5f;
    private static final int MAX_ENTITIES = 512;

    private EspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(EspRenderer::render);
    }

    private static void render(WorldExtractionContext ctx) {
        if (!EspClient.enabled) return;
        EspConfig cfg = EspClient.config();
        if (!cfg.box && !cfg.tracers && !cfg.names) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.worldRenderer == null) return;

        Camera camera = ctx.camera();
        Vec3d cam = camera.getCameraPos();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            Vec3d from = cfg.tracers ? Crosshair.point(ctx, camera) : null;
            int drawn = 0;
            for (Entity p : mc.world.getEntities()) {
                if (!EspClient.isTarget(p)) continue;
                if (++drawn > MAX_ENTITIES) break;

                Vec3d lerped = p.getLerpedPos(tickDelta);
                Box box = p.getBoundingBox().offset(lerped.subtract(p.getEntityPos()));
                double dist = lerped.distanceTo(mc.player.getLerpedPos(tickDelta));
                int color = EspClient.colorFor(p, dist);

                if (cfg.box) {
                    DrawStyle style = cfg.fill
                            ? DrawStyle.filledAndStroked(0xFF000000 | color, 2.0f, 0x30000000 | color)
                            : DrawStyle.stroked(0xFF000000 | color, 2.0f);
                    GizmoDrawing.box(box.expand(0.05), style).ignoreOcclusion();
                }
                if (from != null) {
                    GizmoDrawing.line(from, box.getCenter(), 0xFF000000 | color, 1.5f).ignoreOcclusion();
                }
                if (cfg.names) label(p, box, color, dist, cam, camera, cfg);
            }
        } catch (RuntimeException ex) {
            EspClient.LOGGER.debug("ESP konnte nicht gezeichnet werden", ex);
        }
    }

    private static void label(Entity p, Box b, int color, double dist, Vec3d cam, Camera camera, EspConfig cfg) {
        StringBuilder sb = new StringBuilder(p.getName().getString());
        if (cfg.distance) sb.append(" · ").append(Math.round(dist)).append(" m");
        if (cfg.health && p instanceof LivingEntity living) {
            float hp = living.getHealth() + living.getAbsorptionAmount();
            sb.append(" · ").append(String.format(Locale.ROOT, "%.1f", hp / 2f)).append(" ❤");
        }
        String text = sb.toString();

        Vec3d anchor = new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.5, (b.minZ + b.maxZ) / 2);
        float scale = (float) (BASE_TEXT_SCALE * MathHelper.clamp(anchor.distanceTo(cam) / 8.0, 1.0, 16.0));
        double px = scale / 16.0;

        // Hintergrund in derselben Ebene wie der zur Kamera gedrehte Text
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        int w = MinecraftClient.getInstance().textRenderer.getWidth(text);
        double halfW = (w / 2.0 + 3) * px;
        double top = 2 * px;
        double bottom = -10 * px;
        Vec3d back = anchor.add(forward.multiply(0.02 + dist * 0.004));
        Vec3d a = back.add(right.multiply(-halfW)).add(up.multiply(bottom));
        Vec3d b2 = back.add(right.multiply(halfW)).add(up.multiply(bottom));
        Vec3d c2 = back.add(right.multiply(halfW)).add(up.multiply(top));
        Vec3d d = back.add(right.multiply(-halfW)).add(up.multiply(top));
        GizmoDrawing.quad(a, b2, c2, d, DrawStyle.filled(0x90000000)).ignoreOcclusion();

        // In den Yarn-Namen ist Style.left() die zentrierte Variante
        GizmoDrawing.text(text, anchor, TextGizmo.Style.left(0xFF000000 | brighten(color)).scaled(scale)).ignoreOcclusion();
    }

    private static int brighten(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = r + (255 - r) / 2;
        g = g + (255 - g) / 2;
        b = b + (255 - b) / 2;
        return (r << 16) | (g << 8) | b;
    }
}
