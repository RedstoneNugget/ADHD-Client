package com.adhdclient.headfinder.config;

import com.adhdclient.headfinder.HeadFinderClient;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Einstellungen, gespeichert als config/head_finder/config.json (Gson).
 * Alle Felder sind öffentlich, damit Gson sie direkt liest/schreibt; {@link #sanitize()} korrigiert
 * ungültige Werte nach dem Laden bzw. nach Änderungen im Menü.
 */
public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Tracer-Linie vom Fadenkreuz. */
    public enum TracerMode {
        OFF("Aus"), NEAREST("Nächster"), ALL("Alle");

        public final String title;

        TracerMode(String title) {
            this.title = title;
        }

        public TracerMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * Ein Suchprofil: ein Block-Muster wie bei Base Finder – exakte ID ("minecraft:diamond_ore"),
     * Platzhalter ("*diamond_ore") oder Block-Tag ("#minecraft:logs").
     */
    public static final class SearchProfile {
        /** An/aus, ohne das Profil zu löschen. */
        public boolean enabled = true;
        /** Anzeigename (leer = Muster selbst wird angezeigt). */
        public String name = "";
        /** Block-Muster: exakte ID, "*" als Platzhalter oder "#namespace:tag". */
        public String pattern = "";

        public SearchProfile() {
        }

        public SearchProfile(String name, String pattern) {
            this.name = name;
            this.pattern = pattern;
        }
    }

    public int configVersion = 1;

    // ---------------------------------------------------------------- Anzeige
    /** Boxen durch Wände (ESP). */
    public boolean espEnabled = true;
    /** Schwebendes Label mit Entfernung. */
    public boolean labelsEnabled = true;
    /** Tracer-Linie: aus / nur zum nächsten / zu allen. */
    public TracerMode tracerMode = TracerMode.NEAREST;
    /** HUD-Zähler und Richtungspfeil oben am Bildschirm. */
    public boolean hudEnabled = true;
    /** Abstand des HUD vom oberen Bildschirmrand (z. B. erhöhen, falls Bossleisten im Weg sind). */
    public int hudY = 4;
    /** Gefundene Köpfe gar nicht mehr markieren (sonst grün). */
    public boolean hideFound = true;
    /** Farbe noch offener Köpfe (#RRGGBB). */
    public String colorOpen = "#FF3B3B";
    /** Farbe gefundener Köpfe (#RRGGBB), nur sichtbar, wenn hideFound aus ist. */
    public String colorFound = "#3BFF5A";
    /** Farbe des gewählten Ziels (#RRGGBB). */
    public String colorTarget = "#FFD23B";
    /** Maximale Entfernung (Blöcke) für Boxen, Labels und "Alle"-Tracer. */
    public int maxRange = 256;
    /** Labels nur bis zu dieser Entfernung (Blöcke); das Ziel bzw. der nächste Kopf hat immer eins. Gegen Textsalat bei vielen Köpfen. */
    public int labelRange = 48;
    /** Größe der Labels. */
    public double labelScale = 1.0;

    // ---------------------------------------------------------------- Erkennung
    /** Blöcke als Item auf dem Boden und in Item-Frames ebenfalls erkennen. */
    public boolean detectItems = true;
    /** Rechtsklick auf einen Kopf markiert ihn als gefunden. */
    public boolean foundOnRightClick = true;
    /** Linksklick auf einen Kopf markiert ihn ebenfalls als gefunden. */
    public boolean foundOnLeftClick = false;
    /** Verschwindet ein Kopf-Block (Chunk geladen, Block weg), gilt er als gefunden. */
    public boolean foundOnDisappear = true;
    /** Meldung in der Actionbar, wenn ein neuer Kopf entdeckt wird. */
    public boolean notifyNewHeads = true;
    /** Alle wie viele Ticks die geladenen Chunks erneut geprüft werden (20 Ticks = 1 s). */
    public int rescanIntervalTicks = 20;

    /** Suchprofile – mehrere möglich. */
    public List<SearchProfile> profiles = defaultProfiles();

    public static List<SearchProfile> defaultProfiles() {
        List<SearchProfile> list = new ArrayList<>();
        list.add(new SearchProfile("Diamanterz", "*diamond_ore"));
        return list;
    }

    // ---------------------------------------------------------------- Laden / Speichern

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve(HeadFinderClient.MOD_ID);
    }

    private static Path file() {
        return dir().resolve("config.json");
    }

    public static ModConfig load() {
        Path f = file();
        ModConfig cfg = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, ModConfig.class);
            } catch (IOException | JsonParseException e) {
                HeadFinderClient.LOGGER.warn("config.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
                try {
                    Files.copy(f, f.resolveSibling("config.json.broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
        if (cfg == null) cfg = new ModConfig();
        cfg.sanitize();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Path tmp = file().resolveSibling("config.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            HeadFinderClient.LOGGER.warn("config.json konnte nicht gespeichert werden", e);
        }
    }

    /** Ungültige oder fehlende Werte (z. B. von Hand bearbeitete Datei) korrigieren. */
    public void sanitize() {
        if (tracerMode == null) tracerMode = TracerMode.NEAREST;
        colorOpen = normColor(colorOpen, "#FF3B3B");
        colorFound = normColor(colorFound, "#3BFF5A");
        colorTarget = normColor(colorTarget, "#FFD23B");
        maxRange = Math.max(8, Math.min(4096, maxRange));
        labelScale = Math.max(0.3, Math.min(4.0, labelScale));
        hudY = Math.max(0, Math.min(300, hudY));
        labelRange = Math.max(8, Math.min(4096, labelRange));
        rescanIntervalTicks = Math.max(5, Math.min(200, rescanIntervalTicks));
        if (profiles == null) profiles = defaultProfiles();
        profiles.removeIf(p -> p == null);
        for (SearchProfile p : profiles) {
            if (p.name == null) p.name = "";
            if (p.pattern == null) p.pattern = "";
            p.name = p.name.trim();
            p.pattern = p.pattern.trim();
        }
    }

    // ---------------------------------------------------------------- Farben

    /** "#RRGGBB" → 0xRRGGBB, bei Fehler der Standardwert. */
    public static int rgb(String hex, int fallback) {
        try {
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6) return fallback;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static boolean isValidColor(String hex) {
        return hex != null && rgb(hex, -1) != -1;
    }

    private static String normColor(String hex, String fallback) {
        if (!isValidColor(hex)) return fallback;
        String s = hex.trim();
        if (!s.startsWith("#")) s = "#" + s;
        return s.toUpperCase(Locale.ROOT);
    }
}
