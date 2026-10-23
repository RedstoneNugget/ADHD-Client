package com.adhdclient.butterfly;

import com.adhdclient.gui.L;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Eigene Animationen aus Blockbench: liest die Animationen aus {@code config/adhd_client/butterfly/butterfly.bbmodel}
 * und lädt sie neu, sobald die Datei gespeichert wird. Namen: draw, inspect, attack_1, attack_2 (oder attack), idle.
 * Knochen: hand, blade, handle_a, handle_b – jeweils Position (Modellpixel) und Drehung (Grad).
 */
public final class ButterflyAnimations {

    private static Map<String, Clip> clips = Map.of();
    private static long stamp = Long.MIN_VALUE;
    private static long nextCheck;
    private static String status = "";

    private ButterflyAnimations() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("butterfly").resolve("butterfly.bbmodel");
    }

    /** Kurzer Text für das Menü: was aus der Datei geladen ist. */
    public static String status() {
        reloadIfChanged(true);
        return status;
    }

    /** Eigene Animation für die gerade laufende Animation, sonst null (dann gilt die eingebaute). */
    @Nullable
    static Clip forCurrent() {
        reloadIfChanged(false);
        if (clips.isEmpty()) return null;
        return switch (ButterflyClient.anim()) {
            case EQUIP -> clips.get("draw");
            case INSPECT -> clips.get("inspect");
            case IDLE -> clips.get("idle");
            case ATTACK -> {
                Clip c = clips.get(ButterflyClient.slashDirection() > 0 ? "attack_1" : "attack_2");
                yield c != null ? c : clips.get("attack");
            }
            default -> null;
        };
    }

    private static void reloadIfChanged(boolean now) {
        long t = System.currentTimeMillis();
        if (!now && t < nextCheck) return;
        nextCheck = t + 500;
        Path f = file();
        long m;
        try {
            m = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (Exception e) {
            m = -1;
        }
        if (m == stamp) return;
        stamp = m;
        if (m < 0) {
            clips = Map.of();
            status = L.t("Keine Blockbench-Datei – eingebaute Animationen", "No Blockbench file – built-in animations");
            return;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            clips = parse(JsonParser.parseReader(r).getAsJsonObject());
            status = clips.isEmpty() ? L.t("Datei ohne Animationen – eingebaute werden genutzt", "File has no animations – using built-in ones")
                    : L.t("Eigene Animationen: ", "Own animations: ") + String.join(", ", clips.keySet());
            ButterflyClient.LOGGER.info("Butterfly: Blockbench-Animationen geladen: {}", clips.keySet());
        } catch (Exception e) {
            clips = Map.of();
            status = L.t("Blockbench-Datei fehlerhaft – eingebaute Animationen", "Blockbench file broken – built-in animations");
            ButterflyClient.LOGGER.warn("Butterfly: Blockbench-Datei konnte nicht gelesen werden", e);
        }
    }

    private static Map<String, Clip> parse(JsonObject root) {
        Map<String, Clip> out = new HashMap<>();
        if (!root.has("animations")) return out;
        for (JsonElement ae : root.getAsJsonArray("animations")) {
            JsonObject a = ae.getAsJsonObject();
            String name = a.get("name").getAsString().toLowerCase(Locale.ROOT);
            if (name.startsWith("animation.")) name = name.substring(name.lastIndexOf('.') + 1);
            Clip clip = new Clip(a.has("length") ? a.get("length").getAsFloat() : 0f);
            if (a.has("animators")) {
                for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("animators").entrySet()) {
                    JsonObject an = e.getValue().getAsJsonObject();
                    if (!an.has("name") || !an.has("keyframes")) continue;
                    String bone = an.get("name").getAsString().toLowerCase(Locale.ROOT);
                    for (JsonElement ke : an.getAsJsonArray("keyframes")) {
                        JsonObject k = ke.getAsJsonObject();
                        String channel = k.get("channel").getAsString();
                        int ch = channel.equals("position") ? 0 : channel.equals("rotation") ? 1 : -1;
                        if (ch < 0 || !k.has("data_points")) continue;
                        JsonObject d = k.getAsJsonArray("data_points").get(0).getAsJsonObject();
                        String interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
                        clip.add(bone, ch, new Key(k.get("time").getAsFloat(), num(d, "x"), num(d, "y"), num(d, "z"), interp));
                    }
                }
            }
            clip.finish();
            out.put(name, clip);
        }
        return out;
    }

    private static float num(JsonObject o, String key) {
        if (!o.has(key)) return 0f;
        JsonElement e = o.get(key);
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : Float.parseFloat(e.getAsString().trim());
        } catch (Exception ex) {
            return 0f;   // Molang-Ausdrücke werden nicht ausgewertet
        }
    }

    record Key(float time, float x, float y, float z, String interpolation) {
    }

    /** Eine Animation: je Knochen eine Liste Positions- und eine Liste Dreh-Schlüsselbilder. */
    static final class Clip {
        private final float length;
        private final Map<String, List<Key>[]> bones = new HashMap<>();

        Clip(float length) {
            this.length = length;
        }

        float length() {
            return length;
        }

        @SuppressWarnings("unchecked")
        void add(String bone, int channel, Key key) {
            bones.computeIfAbsent(bone, b -> new List[]{new ArrayList<>(), new ArrayList<>()})[channel].add(key);
        }

        void finish() {
            for (List<Key>[] ch : bones.values()) for (List<Key> l : ch) l.sort(Comparator.comparingDouble(Key::time));
        }

        /** Position x, y, z (Modellpixel) und Drehung x, y, z (Grad) eines Knochens zum Zeitpunkt t. */
        float[] sample(String bone, float t) {
            float[] out = new float[6];
            List<Key>[] ch = bones.get(bone);
            if (ch == null) return out;
            sample(ch[0], t, out, 0);
            sample(ch[1], t, out, 3);
            return out;
        }

        private static void sample(List<Key> keys, float t, float[] out, int o) {
            if (keys.isEmpty()) return;
            int n = keys.size();
            if (t <= keys.get(0).time || n == 1) {
                put(keys.get(0), out, o);
                return;
            }
            if (t >= keys.get(n - 1).time) {
                put(keys.get(n - 1), out, o);
                return;
            }
            int i = 0;
            while (keys.get(i + 1).time < t) i++;
            Key a = keys.get(i), b = keys.get(i + 1);
            float u = (t - a.time) / Math.max(1e-6f, b.time - a.time);
            if (a.interpolation.equals("step")) {
                put(a, out, o);
                return;
            }
            boolean smooth = a.interpolation.equals("catmullrom") || b.interpolation.equals("catmullrom")
                    || a.interpolation.equals("bezier") || b.interpolation.equals("bezier");
            Key p0 = keys.get(Math.max(0, i - 1)), p3 = keys.get(Math.min(n - 1, i + 2));
            out[o] = smooth ? catmull(p0.x, a.x, b.x, p3.x, u) : a.x + (b.x - a.x) * u;
            out[o + 1] = smooth ? catmull(p0.y, a.y, b.y, p3.y, u) : a.y + (b.y - a.y) * u;
            out[o + 2] = smooth ? catmull(p0.z, a.z, b.z, p3.z, u) : a.z + (b.z - a.z) * u;
        }

        private static void put(Key k, float[] out, int o) {
            out[o] = k.x;
            out[o + 1] = k.y;
            out[o + 2] = k.z;
        }

        private static float catmull(float p0, float p1, float p2, float p3, float u) {
            float u2 = u * u, u3 = u2 * u;
            return 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
        }
    }
}
