package com.adhdclient.butterfly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Eigene Modelle aus Blockbench ({@code config/adhd_client/butterfly/models/*.bbmodel}): Würfel und Netze mit
 * eingebetteten Texturen, Knochen (Gruppen) mit Drehpunkten und die Animationen draw, inspect, attack_1/attack_2
 * (oder attack) und idle. Das Modell wird wie das Butterfly gehalten: die Hand hält es bei (0, −6,5, 0), die Spitze
 * zeigt nach oben (+Y). Speichern in Blockbench lädt es sofort neu.
 */
public final class ButterflyCustomModel {

    private static final String[] FACES = {"north", "south", "west", "east", "up", "down"};

    private static String loadedName = "";
    private static long loadedStamp = Long.MIN_VALUE;
    private static long nextCheck;
    @Nullable
    private static Model model;
    private static String status = "";

    private ButterflyCustomModel() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("butterfly").resolve("models");
    }

    /** Alle Modelle im Ordner (Dateinamen ohne .bbmodel), alphabetisch. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                    .map(n -> n.substring(0, n.length() - ".bbmodel".length())).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Kurzer Text fürs Menü: ob das gewählte Modell geladen ist. */
    public static String status() {
        active(true);
        return status;
    }

    /** Das gewählte eigene Modell oder null (dann das eingebaute Butterfly). */
    @Nullable
    static Model active() {
        return active(false);
    }

    @Nullable
    private static Model active(boolean now) {
        String name = ButterflyClient.config().model;
        if (name.isEmpty()) {
            unload();
            status = "";
            return null;
        }
        long t = System.currentTimeMillis();
        if (!now && name.equals(loadedName) && t < nextCheck) return model;
        nextCheck = t + 500;
        Path f = folder().resolve(name + ".bbmodel");
        long stamp;
        try {
            stamp = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (Exception e) {
            stamp = -1;
        }
        if (name.equals(loadedName) && stamp == loadedStamp) return model;
        unload();
        loadedName = name;
        loadedStamp = stamp;
        if (stamp < 0) {
            status = com.adhdclient.gui.L.t("Modell nicht gefunden: ", "Model not found: ") + name;
            return null;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            model = parse(name, JsonParser.parseReader(r).getAsJsonObject());
            status = name + com.adhdclient.gui.L.t(" geladen", " loaded") + (model.clips.isEmpty() ? ""
                    : " – " + String.join(", ", model.clips.keySet()));
            ButterflyClient.LOGGER.info("Butterfly: eigenes Modell {} geladen ({} Animationen)", name, model.clips.size());
        } catch (Exception e) {
            model = null;
            status = com.adhdclient.gui.L.t("Modell fehlerhaft: ", "Model broken: ") + name;
            ButterflyClient.LOGGER.warn("Butterfly: Modell {} konnte nicht gelesen werden", name, e);
        }
        return model;
    }

    private static void unload() {
        if (model != null) for (Identifier id : model.textures) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        model = null;
        loadedName = "";
        loadedStamp = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------ Modell

    /** Ein Knochen: Drehpunkt, Grunddrehung, eigene Flächen je Textur und Kinder. */
    static final class Bone {
        final String name;
        final float[] origin;
        final float[] rotation;
        final Map<Integer, List<float[]>> quads = new HashMap<>();   // Textur → Ecken (je 4 × x, y, z, u, v, nx, ny, nz)
        final List<Bone> children = new ArrayList<>();

        Bone(String name, float[] origin, float[] rotation) {
            this.name = name;
            this.origin = origin;
            this.rotation = rotation;
        }
    }

    static final class Model {
        final Bone root = new Bone("", new float[3], new float[3]);
        final List<Identifier> textures = new ArrayList<>();
        Map<String, ButterflyAnimations.Clip> clips = Map.of();

        /** Animation für den aktuellen Zustand oder null. */
        @Nullable
        ButterflyAnimations.Clip clipFor(ButterflyClient.Anim anim, int dir) {
            return switch (anim) {
                case EQUIP -> clips.get("draw");
                case INSPECT -> clips.get("inspect");
                case IDLE -> clips.get("idle");
                case ATTACK -> {
                    ButterflyAnimations.Clip c = clips.get(dir > 0 ? "attack_1" : "attack_2");
                    yield c != null ? c : clips.get("attack");
                }
                default -> null;
            };
        }

        /** Zeichnen: jeder Knochen wird verschoben und um seinen Drehpunkt gedreht (Z, Y, X wie in Blockbench). */
        void render(MatrixStack m, OrderedRenderCommandQueue queue, int light, @Nullable ButterflyAnimations.Clip clip, float t) {
            draw(root, m, queue, light, clip, t);
        }

        private void draw(Bone b, MatrixStack m, OrderedRenderCommandQueue queue, int light, @Nullable ButterflyAnimations.Clip clip, float t) {
            m.push();
            float[] a = clip != null && !b.name.isEmpty() ? clip.sample(b.name.toLowerCase(Locale.ROOT), t) : new float[6];
            float[] o = b.origin;
            m.translate((o[0] + a[0]) / 16f, (o[1] + a[1]) / 16f, (o[2] + a[2]) / 16f);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(b.rotation[2] + a[5]));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(b.rotation[1] + a[4]));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(b.rotation[0] + a[3]));
            m.translate(-o[0] / 16f, -o[1] / 16f, -o[2] / 16f);
            for (Map.Entry<Integer, List<float[]>> e : b.quads.entrySet()) {
                if (e.getKey() < 0 || e.getKey() >= textures.size()) continue;
                List<float[]> quads = e.getValue();
                queue.submitCustom(m, RenderLayers.entityCutoutNoCull(textures.get(e.getKey())), (entry, vc) -> {
                    for (float[] q : quads) {
                        for (int i = 0; i < 4; i++) {
                            int k = i * 8;
                            vc.vertex(entry, q[k] / 16f, q[k + 1] / 16f, q[k + 2] / 16f).color(0xFFFFFFFF).texture(q[k + 3], q[k + 4])
                                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, q[k + 5], q[k + 6], q[k + 7]);
                        }
                    }
                });
            }
            for (Bone c : b.children) draw(c, m, queue, light, clip, t);
            m.pop();
        }
    }

    // ------------------------------------------------------------ Einlesen

    private static Model parse(String name, JsonObject root) throws Exception {
        Model m = new Model();
        float resW = 16, resH = 16;
        if (root.has("resolution")) {
            JsonObject r = root.getAsJsonObject("resolution");
            resW = r.get("width").getAsFloat();
            resH = r.get("height").getAsFloat();
        }
        // Texturen registrieren und ihre UV-Größe merken
        List<float[]> uvSize = new ArrayList<>();
        String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (root.has("textures")) {
            int i = 0;
            for (JsonElement te : root.getAsJsonArray("textures")) {
                JsonObject t = te.getAsJsonObject();
                String src = t.has("source") ? t.get("source").getAsString() : "";
                int comma = src.indexOf(',');
                Identifier id = Identifier.of("adhd_client", "butterfly_custom/" + key + "/" + i);
                if (comma > 0) {
                    NativeImage img = NativeImage.read(Base64.getDecoder().decode(src.substring(comma + 1)));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
                }
                m.textures.add(id);
                float uw = t.has("uv_width") ? t.get("uv_width").getAsFloat() : resW;
                float uh = t.has("uv_height") ? t.get("uv_height").getAsFloat() : resH;
                uvSize.add(new float[]{uw, uh});
                i++;
            }
        }
        // Elemente nach UUID
        Map<String, JsonObject> elements = new HashMap<>();
        if (root.has("elements")) for (JsonElement e : root.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            elements.put(o.get("uuid").getAsString(), o);
        }
        // Gruppen (neuere Dateien haben sie getrennt, ältere direkt im Outliner)
        Map<String, JsonObject> groups = new HashMap<>();
        if (root.has("groups")) for (JsonElement g : root.getAsJsonArray("groups")) {
            JsonObject o = g.getAsJsonObject();
            groups.put(o.get("uuid").getAsString(), o);
        }
        if (root.has("outliner")) {
            for (JsonElement n : root.getAsJsonArray("outliner")) addNode(m.root, n, elements, groups, uvSize, resW, resH);
        } else {
            for (JsonObject e : elements.values()) addElement(m.root, e, uvSize, resW, resH);
        }
        m.clips = ButterflyAnimations.parse(root);
        return m;
    }

    private static void addNode(Bone parent, JsonElement n, Map<String, JsonObject> elements, Map<String, JsonObject> groups,
                                List<float[]> uvSize, float resW, float resH) {
        if (n.isJsonPrimitive()) {
            JsonObject e = elements.get(n.getAsString());
            if (e != null) addElement(parent, e, uvSize, resW, resH);
            return;
        }
        JsonObject g = n.getAsJsonObject();
        JsonObject data = g.has("uuid") && groups.containsKey(g.get("uuid").getAsString()) ? groups.get(g.get("uuid").getAsString()) : g;
        if (data.has("visibility") && !data.get("visibility").getAsBoolean()) return;
        Bone b = new Bone(data.has("name") ? data.get("name").getAsString() : "", vec(data, "origin"), vec(data, "rotation"));
        parent.children.add(b);
        if (g.has("children")) for (JsonElement c : g.getAsJsonArray("children")) addNode(b, c, elements, groups, uvSize, resW, resH);
    }

    private static void addElement(Bone bone, JsonObject e, List<float[]> uvSize, float resW, float resH) {
        if (e.has("visibility") && !e.get("visibility").getAsBoolean()) return;
        String type = e.has("type") ? e.get("type").getAsString() : "cube";
        Matrix4f rot = new Matrix4f();
        float[] origin = vec(e, "origin"), r = vec(e, "rotation");
        rot.translate(origin[0], origin[1], origin[2])
                .rotateZ((float) Math.toRadians(r[2])).rotateY((float) Math.toRadians(r[1])).rotateX((float) Math.toRadians(r[0]));
        if (type.equals("mesh")) {
            addMesh(bone, e, rot, uvSize, resW, resH);
        } else if (type.equals("cube")) {
            rot.translate(-origin[0], -origin[1], -origin[2]);
            addCube(bone, e, rot, uvSize, resW, resH);
        }
    }

    private static void addCube(Bone bone, JsonObject e, Matrix4f m, List<float[]> uvSize, float resW, float resH) {
        float inf = e.has("inflate") ? e.get("inflate").getAsFloat() : 0f;
        float[] f = vec(e, "from"), t = vec(e, "to");
        float x1 = f[0] - inf, y1 = f[1] - inf, z1 = f[2] - inf, x2 = t[0] + inf, y2 = t[1] + inf, z2 = t[2] + inf;
        if (!e.has("faces")) return;
        JsonObject faces = e.getAsJsonObject("faces");
        float[][][] corners = {
                {{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}},   // north
                {{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}},   // south
                {{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}},   // west
                {{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}},   // east
                {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}},   // up
                {{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}}};  // down
        for (int i = 0; i < 6; i++) {
            if (!faces.has(FACES[i])) continue;
            JsonObject face = faces.getAsJsonObject(FACES[i]);
            int tex = texIndex(face);
            if (tex < 0 || !face.has("uv")) continue;
            JsonArray uv = face.getAsJsonArray("uv");
            float[] size = tex < uvSize.size() ? uvSize.get(tex) : new float[]{resW, resH};
            float u1 = uv.get(0).getAsFloat() / size[0], v1 = uv.get(1).getAsFloat() / size[1];
            float u2 = uv.get(2).getAsFloat() / size[0], v2 = uv.get(3).getAsFloat() / size[1];
            float[][] uvs = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
            int turns = face.has("rotation") ? (face.get("rotation").getAsInt() / 90) & 3 : 0;
            float[][] pts = new float[4][];
            for (int k = 0; k < 4; k++) pts[k] = corners[i][k];
            float[][] tc = new float[4][];
            for (int k = 0; k < 4; k++) tc[k] = uvs[(k + turns) & 3];
            addQuad(bone, tex, m, pts, tc);
        }
    }

    private static void addMesh(Bone bone, JsonObject e, Matrix4f m, List<float[]> uvSize, float resW, float resH) {
        if (!e.has("vertices") || !e.has("faces")) return;
        JsonObject verts = e.getAsJsonObject("vertices");
        for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
            JsonObject face = fe.getValue().getAsJsonObject();
            int tex = texIndex(face);
            if (tex < 0) continue;
            float[] size = tex < uvSize.size() ? uvSize.get(tex) : new float[]{resW, resH};
            JsonArray ids = face.getAsJsonArray("vertices");
            JsonObject uv = face.has("uv") ? face.getAsJsonObject("uv") : new JsonObject();
            int n = ids.size();
            if (n < 3) continue;
            float[][] pts = new float[n][], tc = new float[n][];
            for (int k = 0; k < n; k++) {
                String id = ids.get(k).getAsString();
                JsonArray p = verts.getAsJsonArray(id);
                pts[k] = new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()};
                JsonArray c = uv.has(id) ? uv.getAsJsonArray(id) : null;
                tc[k] = c == null ? new float[2] : new float[]{c.get(0).getAsFloat() / size[0], c.get(1).getAsFloat() / size[1]};
            }
            if (n > 3) sortAround(pts, tc);
            for (int k = 1; k + 1 < n; k++) {
                addQuad(bone, tex, m, new float[][]{pts[0], pts[k], pts[k + 1], pts[k + 1]},
                        new float[][]{tc[0], tc[k], tc[k + 1], tc[k + 1]});
            }
        }
    }

    /** Ecken einer Fläche reihum sortieren (Blockbench speichert Vierecke nicht immer im Kreis). */
    private static void sortAround(float[][] pts, float[][] tc) {
        int n = pts.length;
        float cx = 0, cy = 0, cz = 0;
        for (float[] p : pts) { cx += p[0] / n; cy += p[1] / n; cz += p[2] / n; }
        Vector3f a = new Vector3f(pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]);
        Vector3f b = new Vector3f(pts[2][0] - pts[0][0], pts[2][1] - pts[0][1], pts[2][2] - pts[0][2]);
        Vector3f normal = a.cross(b, new Vector3f());
        if (normal.lengthSquared() < 1e-12f) return;
        normal.normalize();
        Vector3f ax = new Vector3f(pts[0][0] - cx, pts[0][1] - cy, pts[0][2] - cz).normalize();
        Vector3f ay = normal.cross(ax, new Vector3f());
        Integer[] order = new Integer[n];
        double[] ang = new double[n];
        for (int k = 0; k < n; k++) {
            order[k] = k;
            Vector3f d = new Vector3f(pts[k][0] - cx, pts[k][1] - cy, pts[k][2] - cz);
            ang[k] = Math.atan2(d.dot(ay), d.dot(ax));
        }
        java.util.Arrays.sort(order, (i, j) -> Double.compare(ang[i], ang[j]));
        float[][] p2 = new float[n][], t2 = new float[n][];
        for (int k = 0; k < n; k++) { p2[k] = pts[order[k]]; t2[k] = tc[order[k]]; }
        System.arraycopy(p2, 0, pts, 0, n);
        System.arraycopy(t2, 0, tc, 0, n);
    }

    private static void addQuad(Bone bone, int tex, Matrix4f m, float[][] pts, float[][] tc) {
        Vector3f[] w = new Vector3f[4];
        for (int k = 0; k < 4; k++) w[k] = m.transformPosition(new Vector3f(pts[k][0], pts[k][1], pts[k][2]));
        Vector3f n = new Vector3f(w[1]).sub(w[0]).cross(new Vector3f(w[2]).sub(w[0]));
        if (n.lengthSquared() < 1e-12f) n.set(0, 1, 0);
        n.normalize();
        float[] q = new float[32];
        for (int k = 0; k < 4; k++) {
            int o = k * 8;
            q[o] = w[k].x;
            q[o + 1] = w[k].y;
            q[o + 2] = w[k].z;
            q[o + 3] = tc[k][0];
            q[o + 4] = tc[k][1];
            q[o + 5] = n.x;
            q[o + 6] = n.y;
            q[o + 7] = n.z;
        }
        bone.quads.computeIfAbsent(tex, x -> new ArrayList<>()).add(q);
    }

    private static int texIndex(JsonObject face) {
        if (!face.has("texture") || face.get("texture").isJsonNull()) return -1;
        JsonElement t = face.get("texture");
        if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber()) return t.getAsInt();
        if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isBoolean()) return -1;
        try {
            return Integer.parseInt(t.getAsString());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static float[] vec(JsonObject o, String key) {
        float[] v = new float[3];
        if (!o.has(key) || !o.get(key).isJsonArray()) return v;
        JsonArray a = o.getAsJsonArray(key);
        for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        return v;
    }
}
