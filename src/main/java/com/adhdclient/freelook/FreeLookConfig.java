package com.adhdclient.freelook;

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

/** Einstellungen von Free Look, gespeichert als config/adhd_client/free_look.json. */
public final class FreeLookConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Abstand der Kamera zum Spieler in Blöcken (Minecraft-Standard: 4). */
    public double distance = 4;
    /** Kamera geht durch Blöcke (sonst stoppt sie davor wie in Minecraft). */
    public boolean throughBlocks = false;
    /** Taste zum Umschauen (GLFW-Code), im Free-Look-Menü einstellbar. */
    public int key = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
    /** Taste gedrückt halten (sonst einmal drücken zum Umschalten). */
    public boolean hold = true;
    /** Abstand und Durch-Blöcke auch für die normale F5-Ansicht benutzen. */
    public boolean applyToF5 = true;
    /** Mausrad ändert den Abstand, während Free Look aktiv ist. */
    public boolean scrollZoom = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("free_look.json");
    }

    public static FreeLookConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                FreeLookConfig c = GSON.fromJson(r, FreeLookConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                FreeLookClient.LOGGER.warn("free_look.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new FreeLookConfig();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("free_look.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            FreeLookClient.LOGGER.warn("free_look.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        distance = Math.max(1, Math.min(32, distance));
    }
}
