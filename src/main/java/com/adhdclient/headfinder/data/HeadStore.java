package com.adhdclient.headfinder.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alle entdeckten Köpfe des aktuellen Servers (bzw. der aktuellen Einzelspielerwelt).
 *
 * Gespeichert wird pro Server in config/head_finder/found/&lt;server&gt;.json; darin steht pro Kopf die
 * Dimension, so dass jede Welt (Overworld, Nether, …) getrennt bleibt. Positionen früherer Funde
 * bleiben erhalten, auch wenn ihr Chunk gerade nicht geladen ist.
 */
public final class HeadStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Dateiformat. */
    private static final class FileData {
        int version = 1;
        String server = "";
        List<HeadEntry> heads = new ArrayList<>();
    }

    /** Gespeicherte Köpfe (Block/Rahmen), Schlüssel siehe {@link HeadEntry#key()}. */
    private final Map<String, HeadEntry> heads = new LinkedHashMap<>();
    /** Köpfe als Item auf dem Boden – nur solange sie existieren. */
    private final Map<String, HeadEntry> items = new HashMap<>();

    private String serverKey;
    private boolean dirty;
    private long saveAt = -1;
    /** Gewähltes Ziel aus der Liste (Schlüssel) oder null = automatisch der nächste offene Kopf. */
    private String targetKey;

    // ---------------------------------------------------------------- Server wechseln

    public boolean isOpen() {
        return serverKey != null;
    }

    public String serverKey() {
        return serverKey;
    }

    /** Beim Betreten eines Servers / einer Welt. */
    public void open(String key) {
        if (key.equals(serverKey)) return;
        close();
        serverKey = key;
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                FileData data = GSON.fromJson(r, FileData.class);
                if (data != null && data.heads != null) {
                    for (HeadEntry e : data.heads) {
                        if (e == null || e.dim == null || e.kind == null || e.kind == HeadEntry.Kind.ITEM) continue;
                        if (e.foundReason == null) e.foundReason = "";
                        if (e.name == null) e.name = "";
                        heads.put(e.key(), e);
                    }
                }
            } catch (IOException | JsonParseException e) {
                HeadFinderClient.LOGGER.warn("Kopf-Daten {} sind fehlerhaft und werden ignoriert ({})", f.getFileName(), e.getMessage());
                try {
                    Files.copy(f, f.resolveSibling(f.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
        HeadFinderClient.LOGGER.info("Head Finder: {} gespeicherte Köpfe für '{}' geladen", heads.size(), key);
    }

    /** Beim Verlassen: speichern und leeren. */
    public void close() {
        if (serverKey != null && dirty) saveNow();
        heads.clear();
        items.clear();
        targetKey = null;
        serverKey = null;
        dirty = false;
        saveAt = -1;
    }

    // ---------------------------------------------------------------- Abfragen

    public Collection<HeadEntry> persisted() {
        return heads.values();
    }

    /** Alle Köpfe (gespeicherte + Items am Boden). */
    public List<HeadEntry> all() {
        List<HeadEntry> out = new ArrayList<>(heads.size() + items.size());
        out.addAll(heads.values());
        out.addAll(items.values());
        return out;
    }

    public List<HeadEntry> inDimension(String dim) {
        List<HeadEntry> out = new ArrayList<>();
        for (HeadEntry e : heads.values()) if (dim.equals(e.dim)) out.add(e);
        for (HeadEntry e : items.values()) if (dim.equals(e.dim)) out.add(e);
        return out;
    }

    public HeadEntry get(String key) {
        HeadEntry e = heads.get(key);
        return e != null ? e : items.get(key);
    }

    public int countFound() {
        int n = 0;
        for (HeadEntry e : heads.values()) if (e.found) n++;
        return n;
    }

    public int countDiscovered() {
        return heads.size() + items.size();
    }

    /** Nächster noch nicht gefundener Kopf in dieser Dimension (oder null). */
    public HeadEntry nearestOpen(String dim, Vec3d from) {
        HeadEntry best = null;
        double bestD = Double.MAX_VALUE;
        for (HeadEntry e : inDimension(dim)) {
            if (e.found) continue;
            double d = e.center().squaredDistanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Aktuelles Ziel für Tracer/Pfeil: das gewählte Ziel (falls in dieser Dimension), sonst der nächste offene Kopf.
     */
    public HeadEntry currentTarget(String dim, Vec3d from) {
        HeadEntry t = targetKey == null ? null : get(targetKey);
        if (t != null && dim.equals(t.dim)) return t;
        return nearestOpen(dim, from);
    }

    public String targetKey() {
        return targetKey;
    }

    public void setTarget(HeadEntry e) {
        targetKey = e == null ? null : e.key();
    }

    // ---------------------------------------------------------------- Ändern

    /**
     * Kopf entdeckt (Block oder Rahmen). Gibt true zurück, wenn er neu ist.
     */
    public boolean discover(String dim, BlockPos pos, HeadEntry.Kind kind, String name) {
        String key = HeadEntry.key(dim, pos, kind);
        if (heads.containsKey(key)) return false;
        heads.put(key, new HeadEntry(dim, pos.toImmutable(), kind, name));
        markDirty();
        return true;
    }

    /** Item-Köpfe am Boden komplett ersetzen (werden bei jedem Entity-Scan neu ermittelt). */
    public void replaceItems(List<HeadEntry> current) {
        items.clear();
        for (HeadEntry e : current) items.put(e.key(), e);
        if (targetKey != null && targetKey.startsWith("I|") && !items.containsKey(targetKey)) targetKey = null;
    }

    public void setFound(HeadEntry e, boolean found, String reason) {
        if (e == null || e.found == found) return;
        e.found = found;
        e.foundAt = found ? System.currentTimeMillis() : 0;
        e.foundReason = found ? reason : "";
        if (found && e.key().equals(targetKey)) targetKey = null;
        markDirty();
    }

    /** Alle als "nicht gefunden" markieren (Positionen bleiben). Gibt die Anzahl zurück. */
    public int resetFound() {
        int n = 0;
        for (HeadEntry e : heads.values()) {
            if (e.found) {
                e.found = false;
                e.foundAt = 0;
                e.foundReason = "";
                n++;
            }
        }
        if (n > 0) markDirty();
        return n;
    }

    /** Alles vergessen (auch Positionen). Gibt die Anzahl zurück. */
    public int resetAll() {
        int n = heads.size();
        heads.clear();
        items.clear();
        targetKey = null;
        markDirty();
        return n;
    }

    public boolean remove(HeadEntry e) {
        if (e == null) return false;
        boolean removed = heads.remove(e.key()) != null;
        if (removed) {
            if (e.key().equals(targetKey)) targetKey = null;
            markDirty();
        }
        return removed;
    }

    // ---------------------------------------------------------------- Speichern

    private void markDirty() {
        dirty = true;
        if (saveAt < 0) saveAt = System.currentTimeMillis() + 2000;
    }

    /** Jeden Tick: Änderungen gebündelt speichern. */
    public void tick() {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
    }

    public void saveNow() {
        saveAt = -1;
        if (serverKey == null) return;
        dirty = false;
        FileData data = new FileData();
        data.server = serverKey;
        data.heads.addAll(heads.values());
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            HeadFinderClient.LOGGER.warn("Kopf-Daten konnten nicht gespeichert werden", e);
        }
    }

    public Path file() {
        return ModConfig.dir().resolve("found").resolve(safeFileName(serverKey) + ".json");
    }

    /** Servername → sicherer Dateiname (z. B. "mp_donutsmp.net" oder "sp_Neue Welt"). */
    static String safeFileName(String key) {
        String s = key == null ? "unknown" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        if (s.isBlank()) s = "unknown";
        return s.length() > 80 ? s.substring(0, 80) : s;
    }
}
