package com.adhdclient.fullbright;

import com.adhdclient.core.AdhdClientClient;
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

/**
 * Full Bright: alles ist hell wie mit Nachtsicht, ohne Trank und ohne Taste – an/aus nur im ADHD-Menü.
 * Es wird nur die Licht-Tabelle des Clients verändert, der Server bekommt davon nichts mit.
 */
public final class FullBrightClient {

    public static boolean enabled = false;

    private static Config config;

    public static final class Config {
        /** Helligkeit 0–100 %. */
        public int strength = 100;
        /** Dunkelheits-Effekt (Warden, Sculk-Kreischer) ignorieren. */
        public boolean ignoreDarkness = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FullBrightClient() {
    }

    public static Config config() {
        if (config == null) config = load();
        return config;
    }

    /** Nachtsicht-Stärke für die Licht-Tabelle (0–1). */
    public static float strength() {
        return Math.max(0, Math.min(100, config().strength)) / 100f;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("full_bright.json");
    }

    private static Config load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) return c;
            } catch (IOException | JsonParseException e) {
                AdhdClientClient.LOGGER.warn("full_bright.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new Config();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("full_bright.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config(), w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AdhdClientClient.LOGGER.warn("full_bright.json konnte nicht gespeichert werden", e);
        }
    }
}
