package com.adhdclient.blink;

import com.adhdclient.gui.HotKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Einstellungen von Blink, gespeichert als config/adhd_client/blink.json. */
public final class BlinkConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Taste zum An-/Ausschalten (GLFW-Code), im Blink-Menü einstellbar. */
    public int key = HotKey.NONE;
    /** Taste zum Abbrechen: zurückgehaltene Bewegung verwerfen und zurück zum Start. */
    public int cancelKey = HotKey.NONE;
    /** Kopie des Spielers an der Stelle, an der der Server ihn gerade sieht. */
    public boolean fakePlayer = true;
    /** Nach so vielen Sekunden automatisch senden und ausschalten (0 = nie). */
    public int autoSendSeconds = 0;
    /** Sekunden und Paketanzahl in der Actionbar anzeigen. */
    public boolean showInfo = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("blink.json");
    }

    public static BlinkConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                BlinkConfig c = GSON.fromJson(r, BlinkConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                BlinkClient.LOGGER.warn("blink.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new BlinkConfig();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("blink.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BlinkClient.LOGGER.warn("blink.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        autoSendSeconds = Math.max(0, Math.min(30, autoSendSeconds));
    }
}
