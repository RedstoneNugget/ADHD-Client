package com.adhdclient.headfinder.render;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.scan.HeadScanner;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeichnet Boxen, Labels und Tracer über das Gizmo-System von 1.21.11.
 *
 * Gizmos werden in {@link WorldRenderEvents#END_EXTRACTION} innerhalb von
 * {@code worldRenderer.startDrawingGizmos()} jedes Bild neu abgegeben. {@code ignoreOcclusion()} schaltet
 * den Tiefentest ab – dadurch sind die Markierungen durch Blöcke sichtbar.
 */
public final class HeadRenderer {

    private static final float BASE_TEXT_SCALE = 0.5f;

    /** Dauer der letzten Gizmo-Abgabe in Mikrosekunden (für /headfinder status und Tests). */
    public static long lastRenderMicros;
    /** Höchstwert seit dem letzten Zurücksetzen. */
    public static long maxRenderMicros;

    private HeadRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(HeadRenderer::render);
    }

    private static void render(WorldExtractionContext ctx) {
        if (!HeadFinderClient.enabled) return;
        ModConfig cfg = HeadFinderClient.config();
        if (!cfg.espEnabled && cfg.tracerMode == ModConfig.TracerMode.OFF) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        HeadStore store = HeadFinderClient.store();
        if (mc.world == null || mc.worldRenderer == null || !store.isOpen()) return;

        Camera camera = ctx.camera();
        Vec3d cam = camera.getCameraPos();
        String dim = HeadScanner.dimension(mc.world);
        List<HeadEntry> heads = store.inDimension(dim);
        if (heads.isEmpty()) return;

        int open = ModConfig.rgb(cfg.colorOpen, 0xFF3B3B);
        int found = ModConfig.rgb(cfg.colorFound, 0x3BFF5A);
        int targetColor = ModConfig.rgb(cfg.colorTarget, 0xFFD23B);
        double range = cfg.maxRange;
        HeadEntry target = store.currentTarget(dim, cam);
        boolean explicitTarget = store.targetKey() != null && target != null && target.key().equals(store.targetKey());

        long t0 = System.nanoTime();
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            List<HeadEntry> tracerTo = new ArrayList<>();
            for (HeadEntry h : heads) {
                if (h.found && cfg.hideFound) continue;
                Vec3d c = h.center();
                double dist = c.distanceTo(cam);
                boolean isTarget = h == target && !h.found;
                if (dist > range && !isTarget) continue;

                int color = h.found ? found : (isTarget && explicitTarget ? targetColor : open);
                if (cfg.espEnabled) {
                    Box box = box(mc.world, h).expand(0.04);
                    int alpha = h.found ? 0x22 : 0x45;
                    DrawStyle style = DrawStyle.filledAndStroked(0xFF000000 | color, isTarget ? 3.5f : 2.5f, alpha << 24 | color);
                    GizmoDrawing.box(box, style).ignoreOcclusion();
                    if (cfg.labelsEnabled && (dist <= cfg.labelRange || isTarget)) label(h, box, color, dist, isTarget && explicitTarget, cam, camera, cfg);
                }
                if (!h.found && cfg.tracerMode == ModConfig.TracerMode.ALL && !explicitTarget) tracerTo.add(h);
            }

            // Tracer: bei gewähltem Ziel nur dorthin, sonst zum nächsten bzw. zu allen offenen Köpfen
            if (cfg.tracerMode != ModConfig.TracerMode.OFF && target != null && !target.found) {
                if (explicitTarget || cfg.tracerMode == ModConfig.TracerMode.NEAREST) {
                    tracerTo.clear();
                    tracerTo.add(target);
                }
                Vec3d from = crosshairPoint(ctx, camera, cam);
                for (HeadEntry h : tracerTo) {
                    int color = (h == target && explicitTarget) ? targetColor : open;
                    float width = h == target ? 2.5f : 1.5f;
                    GizmoDrawing.line(from, h.center(), 0xFF000000 | brighten(color, 0.25f), width).ignoreOcclusion();
                }
            }
        } catch (RuntimeException e) {
            // Darstellung darf das Spiel nie abstürzen lassen
            HeadFinderClient.LOGGER.debug("Markierungen konnten nicht gezeichnet werden", e);
        }
        lastRenderMicros = (System.nanoTime() - t0) / 1000;
        maxRenderMicros = Math.max(maxRenderMicros, lastRenderMicros);
    }

    /** Vom Mixin gesetzt: echte Projektionsmatrix dieses Bildes (inkl. Kamera-Wackeln). */
    private static final Matrix4f FRAME_PROJECTION = new Matrix4f();
    private static boolean hasFrameProjection;

    public static void setFrameProjection(Matrix4f projection) {
        FRAME_PROJECTION.set(projection);
        hasFrameProjection = true;
    }

    /**
     * Punkt 0,6 Blöcke vor der Kamera, der im Bild genau in der Bildschirmmitte (Fadenkreuz) liegt.
     *
     * Ein einfaches "Kamera + Blickrichtung" reicht nicht: Beim Laufen (View Bobbing) und bei Schaden wird
     * die Ansicht zusätzlich verschoben/gekippt, dann wandert der Linienanfang vom Fadenkreuz weg. Deshalb wird
     * die Bildschirmmitte mit der echten Matrix (Projektion · Kamera-Drehung) zurück in die Welt gerechnet.
     */
    private static Vec3d crosshairPoint(WorldExtractionContext ctx, Camera camera, Vec3d cam) {
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
        // Punkt des Mittelstrahls, der dem Auge am nächsten ist, dann 0,6 Blöcke nach vorn
        Vec3d origin = pa.subtract(dir.multiply(pa.dotProduct(dir)));
        Vec3d p = origin.add(dir.multiply(0.6));
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z) || p.length() > 2) return fallback;
        Vec3d result = cam.add(p);
        lastTracer = new TracerDebug(cam, result, fallback, new Matrix4f(FRAME_PROJECTION).mul(ctx.viewMatrix()));
        return result;
    }

    /** Letzter Tracer-Anfang (für den automatischen Test): Kamera, neuer und alter Startpunkt, Matrix. */
    public record TracerDebug(Vec3d cam, Vec3d from, Vec3d naiveFrom, Matrix4f projView) {
        /** Bildschirmabstand eines Weltpunkts von der Mitte in Pixeln (bei gegebener Fensterhöhe). */
        public double pixelsFromCenter(Vec3d world, int screenW, int screenH) {
            Vector4f v = projView.transform(new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1));
            double nx = v.x / v.w;
            double ny = v.y / v.w;
            return Math.hypot(nx * screenW / 2.0, ny * screenH / 2.0);
        }
    }

    public static volatile TracerDebug lastTracer;

    /** Umriss des Kopfes: echte Block-Form, Entity-Box oder ein Standardwürfel. */
    private static Box box(ClientWorld world, HeadEntry h) {
        BlockPos pos = h.pos();
        switch (h.kind) {
            case ITEM, FRAME -> {
                if (h.kind == HeadEntry.Kind.ITEM && h.entityId >= 0) {
                    Entity e = world.getEntityById(h.entityId);
                    if (e != null) return e.getBoundingBox();
                }
                if (h.kind == HeadEntry.Kind.FRAME) return new Box(pos).contract(0.15);
                Vec3d c = h.center();
                return new Box(c.x - 0.2, c.y - 0.25, c.z - 0.2, c.x + 0.2, c.y + 0.15, c.z + 0.2);
            }
            default -> {
                var chunk = HeadScanner.loadedChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk != null) {
                    BlockState state = chunk.getBlockState(pos);
                    VoxelShape shape = state.getOutlineShape(world, pos);
                    if (!shape.isEmpty()) return shape.getBoundingBox().offset(pos);
                }
                // Standardgröße eines stehenden Kopfes
                return new Box(pos.getX() + 0.25, pos.getY(), pos.getZ() + 0.25, pos.getX() + 0.75, pos.getY() + 0.5, pos.getZ() + 0.75);
            }
        }
    }

    private static void label(HeadEntry h, Box b, int color, double dist, boolean target, Vec3d cam, Camera camera, ModConfig cfg) {
        Vec3d anchor = new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.35, (b.minZ + b.maxZ) / 2);
        String title = h.found ? "✔ Gefunden" : (target ? "Ziel" : "Kopf");
        if (h.kind != HeadEntry.Kind.BLOCK) title += " (" + h.kind.title + ")";
        String text = title + " · " + Math.round(dist) + " m";

        float scale = (float) (BASE_TEXT_SCALE * cfg.labelScale * MathHelper.clamp(anchor.distanceTo(cam) / 8.0, 1.0, 16.0));
        double px = scale / 16.0; // ein Schrift-Pixel in Blöcken

        // Kamera-Ebene: Hintergrund liegt in derselben Ebene wie der zur Kamera gedrehte Text
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        int w = MinecraftClient.getInstance().textRenderer.getWidth(text);
        double halfW = (w / 2.0 + 3) * px;
        double top = 2 * px;
        double bottom = -10 * px;
        // Fläche minimal hinter den Text schieben, sonst legt sie sich über die Schrift
        Vec3d back = anchor.add(forward.multiply(0.02 + dist * 0.004));
        Vec3d a = back.add(right.multiply(-halfW)).add(up.multiply(bottom));
        Vec3d b2 = back.add(right.multiply(halfW)).add(up.multiply(bottom));
        Vec3d c2 = back.add(right.multiply(halfW)).add(up.multiply(top));
        Vec3d d = back.add(right.multiply(-halfW)).add(up.multiply(top));
        GizmoDrawing.quad(a, b2, c2, d, DrawStyle.filled(0x90000000)).ignoreOcclusion();

        // Achtung: in den Yarn-Namen ist Style.left() die zentrierte Variante
        GizmoDrawing.text(text, anchor, TextGizmo.Style.left(0xFF000000 | brighten(color, 0.55f)).scaled(scale)).ignoreOcclusion();
    }

    static int brighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
