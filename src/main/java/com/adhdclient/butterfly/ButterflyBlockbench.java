package com.adhdclient.butterfly;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Schreibt eine Blockbench-Vorlage (butterfly.bbmodel): das Messer als Netze mit Texturen, die Knochen
 * hand → blade → handle_a und hand → handle_b mit ihren Drehpunkten, und die eingebauten Animationen als
 * Schlüsselbilder zum Weiterbearbeiten. Ohne Änderung sieht das Ergebnis im Spiel genauso aus wie eingebaut.
 */
public final class ButterflyBlockbench {

    private static final int TEX_SIZE = 1024;
    private static final float STEP = 0.1f;
    private static final String[] PART_NAMES = {"blade", "handle_a", "handle_b"};
    private static final String[] MATERIAL_NAMES = {"steel", "handle", "pins", "inlay", "plastic"};

    private ButterflyBlockbench() {
    }

    /** Vorlage schreiben (überschreibt eine vorhandene Datei). */
    public static Path write() throws Exception {
        if (!ButterflyRenderer.ensureMesh()) throw new IllegalStateException("Modell nicht geladen");
        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.5");
        meta.addProperty("model_format", "free");
        meta.addProperty("box_uv", false);
        root.add("meta", meta);
        root.addProperty("name", "butterfly");
        JsonObject res = new JsonObject();
        res.addProperty("width", TEX_SIZE);
        res.addProperty("height", TEX_SIZE);
        root.add("resolution", res);

        // Netze
        JsonArray elements = new JsonArray();
        List<List<String>> partElements = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < ButterflyRenderer.parts(); i++) {
            int part = ButterflyRenderer.partOf(i), mat = ButterflyRenderer.materialOf(i);
            String uuid = uuid();
            elements.add(mesh(PART_NAMES[part] + "_" + MATERIAL_NAMES[mat], uuid, mat, ButterflyRenderer.vertsOf(i)));
            partElements.get(part).add(uuid);
        }
        root.add("elements", elements);

        // Knochen: hand → blade → handle_a, hand → handle_b
        float[] grip = ButterflyRenderer.grip(), pinA = ButterflyRenderer.pinA(), pinB = ButterflyRenderer.pinB();
        String handId = uuid(), bladeId = uuid(), aId = uuid(), bId = uuid();
        JsonObject handleA = group(ButterflyRenderer.HANDLE_A, aId, pinA, partElements.get(1), null);
        JsonObject blade = group(ButterflyRenderer.BLADE, bladeId, pinB, partElements.get(0), handleA);
        JsonObject handleB = group(ButterflyRenderer.HANDLE_B, bId, pinB, partElements.get(2), null);
        JsonObject hand = group(ButterflyRenderer.HAND, handId, grip, List.of(), blade);
        hand.getAsJsonArray("children").add(handleB);
        JsonArray outliner = new JsonArray();
        outliner.add(hand);
        root.add("outliner", outliner);

        // Texturen
        JsonArray textures = new JsonArray();
        for (int m = 0; m < ButterflyRenderer.textureCount(); m++) {
            byte[] png;
            try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(ButterflyRenderer.texture(m)).getInputStream()) {
                png = in.readAllBytes();
            }
            JsonObject t = new JsonObject();
            t.addProperty("name", MATERIAL_NAMES[m] + ".png");
            t.addProperty("id", String.valueOf(m));
            t.addProperty("uuid", uuid());
            t.addProperty("width", TEX_SIZE);
            t.addProperty("height", TEX_SIZE);
            t.addProperty("uv_width", TEX_SIZE);
            t.addProperty("uv_height", TEX_SIZE);
            t.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
            textures.add(t);
        }
        root.add("textures", textures);

        // Animationen (eingebaute als Startpunkt)
        JsonArray anims = new JsonArray();
        anims.add(animation("draw", ButterflyClient.Anim.EQUIP, 1, handId, bladeId, aId, bId));
        anims.add(animation("inspect", ButterflyClient.Anim.INSPECT, 1, handId, bladeId, aId, bId));
        anims.add(animation("attack_1", ButterflyClient.Anim.ATTACK, 1, handId, bladeId, aId, bId));
        anims.add(animation("attack_2", ButterflyClient.Anim.ATTACK, -1, handId, bladeId, aId, bId));
        anims.add(animation("idle", ButterflyClient.Anim.IDLE, 1, handId, bladeId, aId, bId));
        root.add("animations", anims);

        Path f = ButterflyAnimations.file();
        Files.createDirectories(f.getParent());
        Files.writeString(f, new Gson().toJson(root), StandardCharsets.UTF_8);
        return f;
    }

    private static JsonObject mesh(String name, String uuid, int material, float[] v) {
        JsonObject e = new JsonObject();
        e.addProperty("name", name);
        e.addProperty("type", "mesh");
        e.addProperty("uuid", uuid);
        e.add("origin", vec(0, 0, 0));
        e.add("rotation", vec(0, 0, 0));
        e.addProperty("export", true);
        e.addProperty("visibility", true);
        JsonObject vertices = new JsonObject(), faces = new JsonObject();
        int vi = 0, fi = 0;
        for (int o = 0; o < v.length; o += 32) {               // Viereck mit doppelter Ecke = Dreieck
            JsonObject face = new JsonObject(), uv = new JsonObject();
            JsonArray ids = new JsonArray();
            for (int k = 0; k < 3; k++) {
                int b = o + k * 8;
                String id = "v" + vi++;
                vertices.add(id, vec(v[b], v[b + 1], v[b + 2]));
                JsonArray tc = new JsonArray();
                tc.add(v[b + 3] * TEX_SIZE);
                tc.add(v[b + 4] * TEX_SIZE);
                uv.add(id, tc);
                ids.add(id);
            }
            face.add("uv", uv);
            face.add("vertices", ids);
            face.addProperty("texture", material);
            faces.add("f" + fi++, face);
        }
        e.add("vertices", vertices);
        e.add("faces", faces);
        return e;
    }

    private static JsonObject group(String name, String uuid, float[] origin, List<String> elements, JsonObject child) {
        JsonObject g = new JsonObject();
        g.addProperty("name", name);
        g.addProperty("uuid", uuid);
        g.add("origin", vec(origin[0], origin[1], origin[2]));
        g.add("rotation", vec(0, 0, 0));
        g.addProperty("export", true);
        g.addProperty("isOpen", true);
        g.addProperty("visibility", true);
        JsonArray children = new JsonArray();
        for (String e : elements) children.add(e);
        if (child != null) children.add(child);
        g.add("children", children);
        return g;
    }

    /** Eingebaute Animation abtasten und in Knochen-Schlüsselbilder umrechnen. */
    private static JsonObject animation(String name, ButterflyClient.Anim anim, int dir, String hand, String blade, String a, String b) {
        float length = ButterflyRenderer.duration(anim);
        Matrix4f rest = ButterflyRenderer.viewMatrix(new ButterflyRenderer.Pose()).invert();
        float[] grip = ButterflyRenderer.grip();
        float fix = ButterflyRenderer.closeFix();
        List<float[]> handKeys = new ArrayList<>(), bladeKeys = new ArrayList<>(), aKeys = new ArrayList<>(), bKeys = new ArrayList<>();
        int steps = Math.round(length / STEP);
        for (int i = 0; i <= steps; i++) {
            float t = Math.min(length, i * STEP);
            ButterflyRenderer.Pose p = new ButterflyRenderer.Pose();
            ButterflyRenderer.builtIn(anim, p, t, dir);
            // Hand: Unterschied zur Ruhehaltung als Verschiebung + Drehung (Z, Y, X) um die Griffmitte
            Matrix4f d = new Matrix4f(rest).mul(ButterflyRenderer.viewMatrix(p));
            double ry = Math.asin(Math.max(-1, Math.min(1, -d.m02())));
            double rx = Math.atan2(d.m12(), d.m22());
            double rz = Math.atan2(d.m01(), d.m00());
            Vector3f rp = d.transformDirection(new Vector3f(grip[0], grip[1], grip[2]));
            float px = d.m30() * 16 - grip[0] + rp.x, py = d.m31() * 16 - grip[1] + rp.y, pz = d.m32() * 16 - grip[2] + rp.z;
            handKeys.add(new float[]{t, px, py, pz, (float) Math.toDegrees(rx), (float) Math.toDegrees(ry), (float) Math.toDegrees(rz)});
            bladeKeys.add(new float[]{t, 0, 0, 0, 0, 0, (float) Math.toDegrees(p.blade)});
            aKeys.add(new float[]{t, 0, 0, 0, 0, 0, (float) Math.toDegrees(p.handleA + ButterflyRenderer.closed(p.blade) * fix - p.blade)});
            bKeys.add(new float[]{t, 0, 0, 0, 0, 0, (float) Math.toDegrees(p.handleB)});
        }
        unwrap(handKeys);
        JsonObject animators = new JsonObject();
        animators.add(hand, animator(ButterflyRenderer.HAND, handKeys, true));
        animators.add(blade, animator(ButterflyRenderer.BLADE, bladeKeys, false));
        animators.add(a, animator(ButterflyRenderer.HANDLE_A, aKeys, false));
        animators.add(b, animator(ButterflyRenderer.HANDLE_B, bKeys, false));
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid());
        o.addProperty("name", name);
        o.addProperty("loop", "once");
        o.addProperty("override", false);
        o.addProperty("length", length);
        o.addProperty("snapping", 20);
        o.add("animators", animators);
        return o;
    }

    /** Winkelsprünge (z. B. von 179° auf -179°) glätten, damit Blockbench nicht falsch herum dreht. */
    private static void unwrap(List<float[]> keys) {
        for (int c = 4; c <= 6; c++) {
            for (int i = 1; i < keys.size(); i++) {
                float prev = keys.get(i - 1)[c];
                float cur = keys.get(i)[c];
                while (cur - prev > 180f) cur -= 360f;
                while (cur - prev < -180f) cur += 360f;
                keys.get(i)[c] = cur;
            }
        }
    }

    private static JsonObject animator(String name, List<float[]> keys, boolean withPosition) {
        JsonArray frames = new JsonArray();
        for (float[] k : keys) {
            if (withPosition) frames.add(keyframe("position", k[0], k[1], k[2], k[3]));
            frames.add(keyframe("rotation", k[0], k[4], k[5], k[6]));
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", "bone");
        o.add("keyframes", frames);
        return o;
    }

    private static JsonObject keyframe(String channel, float time, float x, float y, float z) {
        JsonObject d = new JsonObject();
        d.addProperty("x", round(x));
        d.addProperty("y", round(y));
        d.addProperty("z", round(z));
        JsonArray points = new JsonArray();
        points.add(d);
        JsonObject k = new JsonObject();
        k.addProperty("channel", channel);
        k.add("data_points", points);
        k.addProperty("uuid", uuid());
        k.addProperty("time", round(time));
        k.addProperty("interpolation", "catmullrom");
        return k;
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static JsonArray vec(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
