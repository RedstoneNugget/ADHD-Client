package com.adhdclient.basefinder.render;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.basefinder.config.ModConfig;
import com.adhdclient.basefinder.scan.Evaluator;
import com.adhdclient.basefinder.scan.Marker;
import com.adhdclient.basefinder.scan.ScanManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import java.util.List;

/**
 * Zeichnet die Markierungen über Minecrafts Gizmo-System (seit 1.21.11): Boxen, Linien und Text,
 * die mit ignoreOcclusion() ohne Tiefentest – also durch Wände – gezeichnet werden.
 *
 * Die Gizmos werden jedes Bild am Ende der Extraktionsphase neu abgegeben (ohne Lebensdauer),
 * dadurch gibt es kein Flackern und keine doppelt gezeichneten Flächen.
 */
public final class MarkerRenderer {

    private static final float BASE_TEXT_SCALE = 0.5f;

    private MarkerRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(MarkerRenderer::render);
    }

    private static void render(WorldExtractionContext ctx) {
        if (!BaseFinderClient.enabled) return;
        ModConfig cfg = BaseFinderClient.config();
        if (!cfg.general.markersVisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.worldRenderer == null) return;
        Evaluator.Results results = ScanManager.INSTANCE.results();
        Camera camera = ctx.camera();
        Vec3d cam = camera.getCameraPos();
        View view = new View(camera, cam);

        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            // Fokus aus der Fundliste: nur diesen einen Fund zeigen
            String focus = ScanManager.INSTANCE.focusKey();
            if (focus != null) {
                for (Marker.Mode mode : Marker.Mode.values()) {
                    for (Marker m : results.all.get(mode)) {
                        if (m.key.equals(focus)) {
                            drawMarker(m, brighten(colorOf(mode, cfg), 0.45f), 5.0f, view, cfg);
                            return;
                        }
                    }
                }
            }
            draw(results.get(Marker.Mode.AMETHYST), cfg.amethyst.enabled, ModConfig.rgb(cfg.amethyst.color, 0xB061FF), view, cfg);
            draw(results.get(Marker.Mode.BEES), cfg.bees.enabled, ModConfig.rgb(cfg.bees.color, 0xFFD21F), view, cfg);
            draw(results.get(Marker.Mode.SPAWNERS), cfg.spawners.enabled, ModConfig.rgb(cfg.spawners.color, 0x33E0FF), view, cfg);
            draw(results.get(Marker.Mode.KELP), cfg.kelp.enabled, ModConfig.rgb(cfg.kelp.color, 0x3BD63B), view, cfg);
            draw(results.get(Marker.Mode.ANOMALY), cfg.anomaly.enabled, ModConfig.rgb(cfg.anomaly.color, 0xFF5A1F), view, cfg);
        } catch (RuntimeException e) {
            // Darstellung darf das Spiel nie abstürzen lassen
            BaseFinderClient.LOGGER.debug("Markierungen konnten nicht gezeichnet werden", e);
        }
    }

    private static void draw(List<Marker> markers, boolean enabled, int rgb, View view, ModConfig cfg) {
        if (!enabled || markers.isEmpty()) return;
        int bright = brighten(rgb, 0.45f);
        for (Marker m : markers) {
            drawMarker(m, m.best ? bright : rgb, m.best ? (m.chunkFrame ? 4.0f : 3.5f) : 2.0f, view, cfg);
        }
    }

    private static void drawMarker(Marker m, int color, float width, View view, ModConfig cfg) {
        int alpha = m.chunkFrame ? (m.best ? 0x40 : 0x22) : (m.best ? 0x55 : 0x30);
        DrawStyle style = DrawStyle.filledAndStroked(0xFF000000 | color, width, alpha << 24 | color);
        GizmoDrawing.box(m.box, style).ignoreOcclusion();
        label(m, color, view, cfg);
    }

    public static int colorOf(Marker.Mode mode, ModConfig cfg) {
        return switch (mode) {
            case AMETHYST -> ModConfig.rgb(cfg.amethyst.color, 0xB061FF);
            case BEES -> ModConfig.rgb(cfg.bees.color, 0xFFD21F);
            case SPAWNERS -> ModConfig.rgb(cfg.spawners.color, 0x33E0FF);
            case KELP -> ModConfig.rgb(cfg.kelp.color, 0x3BD63B);
            case ANOMALY -> ModConfig.rgb(cfg.anomaly.color, 0xFF5A1F);
        };
    }

    private static void label(Marker m, int color, View view, ModConfig cfg) {
        Vec3d cam = view.cam;
        Vec3d anchor;
        Box b = m.box;
        if (m.chunkFrame) {
            // Chunk-Mitte; Höhe so nah wie möglich an der Kamera, damit das Label lesbar bleibt
            double y = MathHelper.clamp(cam.y, b.minY + 1.0, Math.max(b.minY + 1.0, b.maxY - 0.5));
            anchor = new Vec3d((b.minX + b.maxX) / 2, y, (b.minZ + b.maxZ) / 2);
        } else {
            anchor = new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.4, (b.minZ + b.maxZ) / 2);
        }
        double dist = anchor.distanceTo(cam);
        float scale = (float) (BASE_TEXT_SCALE * cfg.general.labelScale * MathHelper.clamp(dist / 8.0, 1.0, 12.0));
        double px = scale / 16.0; // ein Schrift-Pixel in Blöcken
        double lineH = 10.0 * px;
        // Beschriftungen hinter der Kamera sind unsichtbar – gar nicht erst abgeben
        if (anchor.subtract(cam).dotProduct(view.forward) < 0 && dist > 2) return;
        // Kamera-Ebene: Text wird zur Kamera gedreht, Zeilen und Hintergrund liegen in derselben Ebene
        Vec3d forward = view.forward;
        Vec3d right = view.right;
        Vec3d up = view.up;

        List<String> lines = new java.util.ArrayList<>(m.lines);
        if (m.distanceLine >= 0 && m.distanceLine < lines.size()) {
            lines.set(m.distanceLine, lines.get(m.distanceLine) + " · " + Math.round(m.target.distanceTo(cam)) + " m");
        }
        int n = lines.size();
        var tr = MinecraftClient.getInstance().textRenderer;
        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, tr.getWidth(s));

        // dunkler, halbtransparenter Hintergrund hinter allen Zeilen
        double halfW = (maxW / 2.0 + 3) * px;
        double top = (n - 1) * lineH + 2 * px;
        double bottom = -9 * px - 1 * px;
        // Fläche minimal hinter den Text schieben, sonst legt sie sich beim Zeichnen über die Schrift
        Vec3d back = anchor.add(forward.multiply(0.02 + dist * 0.004));
        Vec3d a = back.add(right.multiply(-halfW)).add(up.multiply(bottom));
        Vec3d b2 = back.add(right.multiply(halfW)).add(up.multiply(bottom));
        Vec3d c2 = back.add(right.multiply(halfW)).add(up.multiply(top));
        Vec3d d = back.add(right.multiply(-halfW)).add(up.multiply(top));
        GizmoDrawing.quad(a, b2, c2, d, DrawStyle.filled(0x90000000)).ignoreOcclusion();

        int textColor = 0xFF000000 | brighten(color, 0.65f);
        for (int i = 0; i < n; i++) {
            int c = (i == 0 && m.best) ? 0xFFFFE066 : (i == 0 ? 0xFFFFFFFF : textColor);
            Vec3d pos = anchor.add(up.multiply((n - 1 - i) * lineH));
            // Achtung: in den Yarn-Namen ist Style.left() die zentrierte Variante (adjustLeft leer -> -Breite/2)
            GizmoDrawing.text(lines.get(i), pos, TextGizmo.Style.left(c).scaled(scale)).ignoreOcclusion();
        }
    }

    /** Kamera-Achsen, einmal pro Bild berechnet. */
    private static final class View {
        final Vec3d cam;
        final Vec3d forward;
        final Vec3d right;
        final Vec3d up;

        View(Camera camera, Vec3d cam) {
            this.cam = cam;
            this.forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            Vec3d r = forward.crossProduct(new Vec3d(0, 1, 0));
            this.right = r.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : r.normalize();
            this.up = right.crossProduct(forward).normalize();
        }
    }

    private static int brighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
