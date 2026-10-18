package com.adhdclient.freecam;

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

/** Einstellungen von Free Cam, gespeichert als config/adhd_client/free_cam.json. */
public final class FreeCamConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Flug-Geschwindigkeit in Blöcken pro Sekunde. */
    public double speed = 12;
    /** Taste zum An-/Ausschalten (GLFW-Code), im Free-Cam-Menü einstellbar. */
    public int key = org.lwjgl.glfw.GLFW.GLFW_KEY_F6;
    /** Sanftes Beschleunigen und Abbremsen. */
    public boolean smooth = true;
    /** Faktor beim Sprinten (Sprint-Taste gedrückt). */
    public double sprintFactor = 3;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("free_cam.json");
    }

    public static FreeCamConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                FreeCamConfig c = GSON.fromJson(r, FreeCamConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                FreeCamClient.LOGGER.warn("free_cam.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new FreeCamConfig();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("free_cam.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            FreeCamClient.LOGGER.warn("free_cam.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        speed = Math.max(1, Math.min(100, speed));
        sprintFactor = Math.max(1, Math.min(10, sprintFactor));
    }
}
