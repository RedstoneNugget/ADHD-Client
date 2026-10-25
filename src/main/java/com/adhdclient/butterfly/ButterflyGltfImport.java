package com.adhdclient.butterfly;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Wandelt glTF-Modelle (.gltf mit Dateien daneben, .glb oder ein Zip, z. B. von Sketchfab) aus dem Modelle-Ordner in
 * Blockbench-Dateien (.bbmodel) um. Jedes Teil mit eigenem Knoten wird ein Knochen, Texturen werden eingebettet,
 * das Modell wird so gedreht und skaliert, dass die längste Seite nach oben zeigt und es wie das Butterfly in der
 * Hand liegt. Dateien werden nur gelesen, nichts wird entpackt oder ausgeführt.
 */
public final class ButterflyGltfImport {

    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final float LENGTH = 27f;      // Länge im Modell (wie das eingebaute Butterfly)
    private static final int MAX_TEX = 1024;

    private ButterflyGltfImport() {
    }

    /** Alle glTF/GLB/Zip-Dateien im Ordner umwandeln, für die es noch keine .bbmodel gibt. Gibt die neuen Namen zurück. */
    public static List<String> convertAll() {
        List<String> done = new ArrayList<>();
        Path dir = ButterflyCustomModel.folder();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".gltf") || n.endsWith(".glb") || n.endsWith(".zip");
            }).forEach(files::add);
        } catch (Exception e) {
            return done;
        }
        for (Path f : files) {
            String file = f.getFileName().toString();
            String name = file.substring(0, file.lastIndexOf('.'));
            Path out = dir.resolve(name + ".bbmodel");
            if (Files.exists(out)) continue;
            try {
                if (Files.size(f) > MAX_BYTES) throw new IllegalStateException("Datei zu groß");
                Map<String, byte[]> files2 = read(f);
                JsonObject bb = convert(name, files2);
                Files.writeString(out, new Gson().toJson(bb), StandardCharsets.UTF_8);
                done.add(name);
                ButterflyClient.LOGGER.info("Butterfly: {} in {}.bbmodel umgewandelt", file, name);
            } catch (Exception e) {
                ButterflyClient.LOGGER.warn("Butterfly: {} konnte nicht umgewandelt werden", file, e);
            }
        }
        return done;
    }

    /** Dateien einsammeln: Zip im Speicher lesen, sonst die Datei selbst und alles daneben, worauf sie verweist. */
    private static Map<String, byte[]> read(Path f) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        String lower = f.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            long total = 0;
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(f))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    byte[] data = zin.readNBytes((int) Math.min(MAX_BYTES, Integer.MAX_VALUE - 8));
                    total += data.length;
                    if (total > MAX_BYTES) throw new IllegalStateException("Zip zu groß");
                    files.put(e.getName().replace('\\', '/'), data);
                }
            }
        } else {
            files.put(f.getFileName().toString(), Files.readAllBytes(f));
            if (lower.endsWith(".gltf")) {
                JsonObject g = JsonParser.parseString(new String(files.get(f.getFileName().toString()), StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : new String[]{"buffers", "images"}) {
                    if (!g.has(key)) continue;
                    for (JsonElement b : g.getAsJsonArray(key)) {
                        JsonObject o = b.getAsJsonObject();
                        if (!o.has("uri") || o.get("uri").getAsString().startsWith("data:")) continue;
                        String uri = java.net.URLDecoder.decode(o.get("uri").getAsString(), StandardCharsets.UTF_8);
                        Path p = f.getParent().resolve(uri).normalize();
                        if (p.startsWith(f.getParent()) && Files.exists(p)) files.put(uri.replace('\\', '/'), Files.readAllBytes(p));
                    }
                }
            }
        }
        return files;
    }

    // ------------------------------------------------------------ Umwandeln

    private record Prim(String bone, int material, List<float[]> tris) {      // je Dreieck 3 × (x, y, z, u, v)
    }

    private static JsonObject convert(String name, Map<String, byte[]> files) throws Exception {
        // glTF-Text und Binärdaten finden
        JsonObject g = null;
        String base = "";
        ByteBuffer glbBin = null;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            if (n.endsWith(".glb")) {
                ByteBuffer b = ByteBuffer.wrap(e.getValue()).order(ByteOrder.LITTLE_ENDIAN);
                int jsonLen = b.getInt(12);
                g = JsonParser.parseString(new String(e.getValue(), 20, jsonLen, StandardCharsets.UTF_8)).getAsJsonObject();
                int binStart = 20 + jsonLen;
                if (binStart + 8 <= e.getValue().length) {
                    int binLen = b.getInt(binStart);
                    glbBin = ByteBuffer.wrap(e.getValue(), binStart + 8, binLen).slice().order(ByteOrder.LITTLE_ENDIAN);
                }
                break;
            }
            if (n.endsWith(".gltf")) {
                g = JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                int slash = e.getKey().lastIndexOf('/');
                base = slash >= 0 ? e.getKey().substring(0, slash + 1) : "";
                break;
            }
        }
        if (g == null) throw new IllegalStateException("Keine .gltf oder .glb gefunden");

        List<ByteBuffer> buffers = new ArrayList<>();
        if (g.has("buffers")) for (JsonElement be : g.getAsJsonArray("buffers")) {
            JsonObject b = be.getAsJsonObject();
            if (!b.has("uri")) {
                buffers.add(glbBin);
            } else {
                buffers.add(ByteBuffer.wrap(resource(files, base, b.get("uri").getAsString())).order(ByteOrder.LITTLE_ENDIAN));
            }
        }

        // Knoten durchgehen und Dreiecke in Weltkoordinaten sammeln
        List<Prim> prims = new ArrayList<>();
        JsonArray nodes = g.has("nodes") ? g.getAsJsonArray("nodes") : new JsonArray();
        List<Integer> roots = new ArrayList<>();
        if (g.has("scenes")) {
            int scene = g.has("scene") ? g.get("scene").getAsInt() : 0;
            for (JsonElement n : g.getAsJsonArray("scenes").get(scene).getAsJsonObject().getAsJsonArray("nodes")) roots.add(n.getAsInt());
        } else {
            for (int i = 0; i < nodes.size(); i++) roots.add(i);
        }
        Set<String> used = new HashSet<>();
        for (int r : roots) walk(g, nodes, r, new Matrix4f(), buffers, prims, used);
        if (prims.isEmpty()) throw new IllegalStateException("Keine Dreiecke gefunden");

        // Ausrichten: längste Seite nach oben, auf Butterfly-Länge skalieren, Hand bei y = −6,5
        float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Prim p : prims) for (float[] t : p.tris) for (int k = 0; k < 3; k++) for (int a = 0; a < 3; a++) {
            mn[a] = Math.min(mn[a], t[k * 5 + a]);
            mx[a] = Math.max(mx[a], t[k * 5 + a]);
        }
        int longest = 0;
        for (int a = 1; a < 3; a++) if (mx[a] - mn[a] > mx[longest] - mn[longest]) longest = a;
        float scale = LENGTH / Math.max(1e-6f, mx[longest] - mn[longest]);
        Matrix4f fit = new Matrix4f();
        if (longest == 0) fit.rotateZ((float) Math.PI / 2);        // x → y
        else if (longest == 2) fit.rotateX(-(float) Math.PI / 2);  // z → y
        fit.scale(scale).translate(-(mn[0] + mx[0]) / 2, -(mn[1] + mx[1]) / 2, -(mn[2] + mx[2]) / 2);
        // nach dem Drehen unten bei −14 beginnen (wie das Griffende des Butterfly)
        float minY = Float.MAX_VALUE;
        for (Prim p : prims) for (float[] t : p.tris) for (int k = 0; k < 3; k++) {
            minY = Math.min(minY, fit.transformPosition(new Vector3f(t[k * 5], t[k * 5 + 1], t[k * 5 + 2])).y);
        }
        new Matrix4f().translate(0, -14f - minY, 0).mul(fit, fit);

        // Texturen
        List<JsonObject> textures = new ArrayList<>();
        List<int[]> texSize = new ArrayList<>();
        Map<Integer, Integer> materialTex = new HashMap<>();
        JsonArray materials = g.has("materials") ? g.getAsJsonArray("materials") : new JsonArray();
        for (int m = 0; m < materials.size(); m++) {
            JsonObject pbr = materials.get(m).getAsJsonObject().has("pbrMetallicRoughness")
                    ? materials.get(m).getAsJsonObject().getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
            BufferedImage img = null;
            if (pbr.has("baseColorTexture")) {
                int ti = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                JsonObject tex = g.getAsJsonArray("textures").get(ti).getAsJsonObject();
                if (tex.has("source")) img = image(g, files, base, buffers, tex.get("source").getAsInt());
            }
            if (img == null) {
                float[] c = {1, 1, 1, 1};
                if (pbr.has("baseColorFactor")) for (int i = 0; i < 4; i++) c[i] = pbr.getAsJsonArray("baseColorFactor").get(i).getAsFloat();
                img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
                int argb = 0xFF000000 | (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, argb);
            }
            materialTex.put(m, textures.size());
            textures.add(texture(materials.get(m).getAsJsonObject().has("name") ? materials.get(m).getAsJsonObject().get("name").getAsString() : "material" + m, img));
            texSize.add(new int[]{img.getWidth(), img.getHeight()});
        }
        int fallback = -1;

        // Elemente und Knochen
        JsonArray elements = new JsonArray();
        Map<String, JsonArray> boneChildren = new HashMap<>();
        Map<String, float[]> boneOrigin = new HashMap<>();
        for (Prim p : prims) {
            int tex = materialTex.getOrDefault(p.material, -1);
            if (tex < 0) {
                if (fallback < 0) {
                    BufferedImage w = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) w.setRGB(x, y, 0xFFB0B0B0);
                    fallback = textures.size();
                    textures.add(texture("grey", w));
                    texSize.add(new int[]{4, 4});
                }
                tex = fallback;
            }
            int[] size = texSize.get(tex);
            JsonObject el = new JsonObject();
            String uuid = UUID.randomUUID().toString();
            el.addProperty("name", p.bone);
            el.addProperty("type", "mesh");
            el.addProperty("uuid", uuid);
            el.add("origin", arr(0, 0, 0));
            el.add("rotation", arr(0, 0, 0));
            JsonObject verts = new JsonObject(), faces = new JsonObject();
            int vi = 0, fi = 0;
            float cx = 0, cy = 0, cz = 0;
            int count = 0;
            for (float[] t : p.tris) {
                JsonObject face = new JsonObject(), uv = new JsonObject();
                JsonArray ids = new JsonArray();
                for (int k = 0; k < 3; k++) {
                    Vector3f v = fit.transformPosition(new Vector3f(t[k * 5], t[k * 5 + 1], t[k * 5 + 2]));
                    String id = "v" + vi++;
                    verts.add(id, arr(v.x, v.y, v.z));
                    JsonArray c = new JsonArray();
                    c.add(round(t[k * 5 + 3] * size[0]));
                    c.add(round(t[k * 5 + 4] * size[1]));
                    uv.add(id, c);
                    ids.add(id);
                    cx += v.x;
                    cy += v.y;
                    cz += v.z;
                    count++;
                }
                face.add("uv", uv);
                face.add("vertices", ids);
                face.addProperty("texture", tex);
                faces.add("f" + fi++, face);
            }
            el.add("vertices", verts);
            el.add("faces", faces);
            elements.add(el);
            boneChildren.computeIfAbsent(p.bone, b -> new JsonArray()).add(uuid);
            boneOrigin.putIfAbsent(p.bone, new float[]{cx / count, cy / count, cz / count});
        }
        JsonArray outliner = new JsonArray();
        for (Map.Entry<String, JsonArray> b : boneChildren.entrySet()) {
            JsonObject grp = new JsonObject();
            float[] o = boneOrigin.get(b.getKey());
            grp.addProperty("name", b.getKey());
            grp.addProperty("uuid", UUID.randomUUID().toString());
            grp.add("origin", arr(o[0], o[1], o[2]));
            grp.add("rotation", arr(0, 0, 0));
            grp.addProperty("isOpen", true);
            grp.add("children", b.getValue());
            outliner.add(grp);
        }

        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.5");
        meta.addProperty("model_format", "free");
        meta.addProperty("box_uv", false);
        root.add("meta", meta);
        root.addProperty("name", name);
        JsonObject res = new JsonObject();
        res.addProperty("width", 16);
        res.addProperty("height", 16);
        root.add("resolution", res);
        root.add("elements", elements);
        root.add("outliner", outliner);
        JsonArray texArr = new JsonArray();
        for (int i = 0; i < textures.size(); i++) {
            textures.get(i).addProperty("id", String.valueOf(i));
            texArr.add(textures.get(i));
        }
        root.add("textures", texArr);
        root.add("animations", new JsonArray());
        return root;
    }

    private static void walk(JsonObject g, JsonArray nodes, int index, Matrix4f parent, List<ByteBuffer> buffers, List<Prim> out, Set<String> used) {
        JsonObject n = nodes.get(index).getAsJsonObject();
        Matrix4f local = new Matrix4f();
        if (n.has("matrix")) {
            float[] m = new float[16];
            JsonArray a = n.getAsJsonArray("matrix");
            for (int i = 0; i < 16; i++) m[i] = a.get(i).getAsFloat();
            local.set(m);
        } else {
            if (n.has("translation")) {
                JsonArray t = n.getAsJsonArray("translation");
                local.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
            }
            if (n.has("rotation")) {
                JsonArray r = n.getAsJsonArray("rotation");
                local.rotate(new Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), r.get(3).getAsFloat()));
            }
            if (n.has("scale")) {
                JsonArray s = n.getAsJsonArray("scale");
                local.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
            }
        }
        Matrix4f world = new Matrix4f(parent).mul(local);
        if (n.has("mesh")) {
            JsonObject mesh = g.getAsJsonArray("meshes").get(n.get("mesh").getAsInt()).getAsJsonObject();
            String bone = boneName(n.has("name") ? n.get("name").getAsString() : mesh.has("name") ? mesh.get("name").getAsString() : "part", used);
            for (JsonElement pe : mesh.getAsJsonArray("primitives")) {
                JsonObject prim = pe.getAsJsonObject();
                if (prim.has("mode") && prim.get("mode").getAsInt() != 4) continue;
                JsonObject at = prim.getAsJsonObject("attributes");
                if (!at.has("POSITION")) continue;
                float[] pos = floats(g, buffers, at.get("POSITION").getAsInt());
                float[] uv = at.has("TEXCOORD_0") ? floats(g, buffers, at.get("TEXCOORD_0").getAsInt()) : null;
                int vertexCount = pos.length / 3;
                int[] idx;
                if (prim.has("indices")) {
                    idx = ints(g, buffers, prim.get("indices").getAsInt());
                } else {
                    idx = new int[vertexCount];
                    for (int i = 0; i < vertexCount; i++) idx[i] = i;
                }
                List<float[]> tris = new ArrayList<>();
                for (int i = 0; i + 2 < idx.length; i += 3) {
                    float[] t = new float[15];
                    for (int k = 0; k < 3; k++) {
                        int v = idx[i + k];
                        Vector3f p = world.transformPosition(new Vector3f(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]));
                        t[k * 5] = p.x;
                        t[k * 5 + 1] = p.y;
                        t[k * 5 + 2] = p.z;
                        if (uv != null) {
                            t[k * 5 + 3] = uv[v * 2];
                            t[k * 5 + 4] = uv[v * 2 + 1];
                        }
                    }
                    tris.add(t);
                }
                out.add(new Prim(bone, prim.has("material") ? prim.get("material").getAsInt() : -1, tris));
            }
        }
        if (n.has("children")) for (JsonElement c : n.getAsJsonArray("children")) walk(g, nodes, c.getAsInt(), world, buffers, out, used);
    }

    private static String boneName(String raw, Set<String> used) {
        String b = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (b.isEmpty()) b = "part";
        String n = b;
        for (int i = 2; used.contains(n); i++) n = b + "_" + i;
        used.add(n);
        return n;
    }

    // ------------------------------------------------------------ Daten lesen

    private static ByteBuffer view(JsonObject g, List<ByteBuffer> buffers, int accessor, int[] strideOut) {
        JsonObject acc = g.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        JsonObject bv = g.getAsJsonArray("bufferViews").get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        ByteBuffer buf = buffers.get(bv.has("buffer") ? bv.get("buffer").getAsInt() : 0).duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int off = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
        strideOut[0] = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 0;
        buf.position(buf.position() + off);
        return buf.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private static float[] floats(JsonObject g, List<ByteBuffer> buffers, int accessor) {
        JsonObject acc = g.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        int comps = switch (acc.get("type").getAsString()) {
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> 1;
        };
        int count = acc.get("count").getAsInt(), type = acc.get("componentType").getAsInt();
        int size = type == 5126 ? 4 : type == 5123 ? 2 : 1;
        int[] stride = new int[1];
        ByteBuffer b = view(g, buffers, accessor, stride);
        int step = stride[0] > 0 ? stride[0] : comps * size;
        float[] out = new float[count * comps];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < comps; c++) {
                int o = i * step + c * size;
                out[i * comps + c] = type == 5126 ? b.getFloat(o) : type == 5123 ? (b.getShort(o) & 0xFFFF) / 65535f : (b.get(o) & 0xFF) / 255f;
            }
        }
        return out;
    }

    private static int[] ints(JsonObject g, List<ByteBuffer> buffers, int accessor) {
        JsonObject acc = g.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        int count = acc.get("count").getAsInt(), type = acc.get("componentType").getAsInt();
        int[] stride = new int[1];
        ByteBuffer b = view(g, buffers, accessor, stride);
        int size = type == 5125 ? 4 : type == 5123 ? 2 : 1;
        int step = stride[0] > 0 ? stride[0] : size;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int o = i * step;
            out[i] = type == 5125 ? b.getInt(o) : type == 5123 ? b.getShort(o) & 0xFFFF : b.get(o) & 0xFF;
        }
        return out;
    }

    private static byte[] resource(Map<String, byte[]> files, String base, String uri) {
        if (uri.startsWith("data:")) return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        String u = java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8).replace('\\', '/');
        byte[] d = files.get(base + u);
        if (d == null) d = files.get(u);
        if (d == null) {
            String tail = u.substring(u.lastIndexOf('/') + 1);
            for (Map.Entry<String, byte[]> e : files.entrySet()) if (e.getKey().endsWith("/" + tail) || e.getKey().equals(tail)) return e.getValue();
            throw new IllegalStateException("Datei fehlt: " + uri);
        }
        return d;
    }

    private static BufferedImage image(JsonObject g, Map<String, byte[]> files, String base, List<ByteBuffer> buffers, int index) throws Exception {
        JsonObject img = g.getAsJsonArray("images").get(index).getAsJsonObject();
        byte[] data;
        if (img.has("uri")) {
            data = resource(files, base, img.get("uri").getAsString());
        } else {
            JsonObject bv = g.getAsJsonArray("bufferViews").get(img.get("bufferView").getAsInt()).getAsJsonObject();
            ByteBuffer buf = buffers.get(bv.has("buffer") ? bv.get("buffer").getAsInt() : 0).duplicate();
            int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
            data = new byte[bv.get("byteLength").getAsInt()];
            buf.position(buf.position() + off);
            buf.get(data);
        }
        try (InputStream in = new ByteArrayInputStream(data)) {
            return ImageIO.read(in);
        }
    }

    /** Textur als PNG einbetten (höchstens 1024 Pixel, ohne Metadaten). */
    private static JsonObject texture(String name, BufferedImage src) throws Exception {
        int w = src.getWidth(), h = src.getHeight();
        float f = Math.min(1f, (float) MAX_TEX / Math.max(w, h));
        int nw = Math.max(1, Math.round(w * f)), nh = Math.max(1, Math.round(h * f));
        BufferedImage img = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        JsonObject t = new JsonObject();
        t.addProperty("name", name + ".png");
        t.addProperty("uuid", UUID.randomUUID().toString());
        t.addProperty("width", nw);
        t.addProperty("height", nh);
        t.addProperty("uv_width", nw);
        t.addProperty("uv_height", nh);
        t.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        return t;
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static JsonArray arr(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }
}
