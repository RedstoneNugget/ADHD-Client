package com.adhdclient.inspect.world;

import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.config.InspectConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Markierungen in der Welt: Umriss + senkrechter Strahl + Beschriftung
 * "X, Y, Z (Distanz m)". Gezeichnet über das Gizmo-System von 1.21.11;
 * ignoreOcclusion() legt sie in den Always-on-top-Pass, dadurch sind sie
 * durch Wände sichtbar.
 */
public final class WorldMarkers {

    private static final int MAX_MARKERS = 64;
    private static final int BEAM_HEIGHT = 96;

    public record Marker(BlockPos pos, String dimension, String label) {
    }

    private static final List<Marker> MARKERS = new ArrayList<>();

    private WorldMarkers() {
    }

    @Nullable
    public static String currentDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null ? client.world.getRegistryKey().getValue().toString() : null;
    }

    public static boolean isMarked(BlockPos pos, @Nullable String dimension) {
        String dim = normalize(dimension);
        for (Marker m : MARKERS) {
            if (m.pos.equals(pos) && m.dimension.equals(dim)) {
                return true;
            }
        }
        return false;
    }

    /** Schaltet die Markierung um. Rückgabe: true = jetzt markiert. */
    public static boolean toggle(BlockPos pos, @Nullable String dimension, String label) {
        String dim = normalize(dimension);
        Iterator<Marker> it = MARKERS.iterator();
        while (it.hasNext()) {
            Marker m = it.next();
            if (m.pos.equals(pos) && m.dimension.equals(dim)) {
                it.remove();
                return false;
            }
        }
        add(pos, dim, label);
        return true;
    }

    public static void add(BlockPos pos, @Nullable String dimension, String label) {
        String dim = normalize(dimension);
        MARKERS.removeIf(m -> m.pos.equals(pos) && m.dimension.equals(dim));
        while (MARKERS.size() >= MAX_MARKERS) {
            MARKERS.remove(0);
        }
        MARKERS.add(new Marker(pos.toImmutable(), dim, label));
    }

    public static int clear() {
        int n = MARKERS.size();
        MARKERS.clear();
        return n;
    }

    public static int count() {
        return MARKERS.size();
    }

    public static List<Marker> all() {
        return List.copyOf(MARKERS);
    }

    private static String normalize(@Nullable String dimension) {
        if (dimension == null || dimension.isBlank()) {
            String cur = currentDimension();
            return cur != null ? cur : "minecraft:overworld";
        }
        Identifier id = Identifier.tryParse(dimension);
        return id != null ? id.toString() : dimension;
    }

    public static void render(WorldExtractionContext context) {
        if (MARKERS.isEmpty()) {
            return;
        }
        String dim = currentDimension();
        if (dim == null) {
            return;
        }
        Vec3d cam = context.camera().getCameraPos();
        int base = InspectConfig.get().markerColor();
        float pulse = 0.65f + 0.35f * MathHelper.sin((Util.getMeasuringTimeMs() % 1600L) / 1600f * MathHelper.TAU);
        int stroke = withAlpha(base, pulse);
        int fill = withAlpha(base, pulse * 0.19f);
        int textColor = withAlpha(lighten(base), 1f);

        try (var scope = context.worldRenderer().startDrawingGizmos()) {
            for (Marker m : MARKERS) {
                if (!m.dimension.equals(dim)) {
                    continue;
                }
                BlockPos pos = m.pos;
                Vec3d center = Vec3d.ofCenter(pos);
                double dist = cam.distanceTo(center);

                GizmoDrawing.box(pos, 0.02f, DrawStyle.filledAndStroked(stroke, 3.0f, fill)).ignoreOcclusion();
                GizmoDrawing.line(
                        new Vec3d(center.x, pos.getY() + 1.0, center.z),
                        new Vec3d(center.x, pos.getY() + 1.0 + BEAM_HEIGHT, center.z),
                        stroke, 4.0f).ignoreOcclusion();

                // Text wächst mit der Entfernung, damit er lesbar bleibt.
                float scale = TextGizmo.Style.DEFAULT_SCALE * (float) MathHelper.clamp(dist / 3.0, 1.0, 60.0);
                double lineHeight = 10.0 * scale / 16.0;
                double top = pos.getY() + 1.25 + lineHeight * 2;
                String main = String.format(Locale.ROOT, "%d, %d, %d (%.0f m)", pos.getX(), pos.getY(), pos.getZ(), dist);
                // Yarn: Style.left() ist horizontal zentriert.
                GizmoDrawing.text(main, new Vec3d(center.x, top, center.z),
                        TextGizmo.Style.left(textColor).scaled(scale)).ignoreOcclusion();
                if (!m.label.isEmpty()) {
                    GizmoDrawing.text(m.label, new Vec3d(center.x, top - lineHeight, center.z),
                            TextGizmo.Style.left(stroke).scaled(scale * 0.8f)).ignoreOcclusion();
                }
            }
        } catch (RuntimeException e) {
            InspectClient.LOGGER.debug("Fehler beim Zeichnen der Markierungen", e);
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = MathHelper.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = r + (255 - r) * 2 / 3;
        g = g + (255 - g) * 2 / 3;
        b = b + (255 - b) * 2 / 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
