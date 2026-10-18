package com.adhdclient.esp;

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

/** Einstellungen des ESP, gespeichert als config/adhd_client/esp.json. */
public final class EspConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Woher die Farbe einer Entity kommt. */
    public enum ColorMode {
        DISTANCE("Entfernung", "Distance"), TEAM("Team", "Team"), FIXED("Feste Farbe", "Fixed color");

        private final String de;
        private final String en;

        ColorMode(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String title() {
            return com.adhdclient.gui.L.t(de, en);
        }

        public ColorMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Ausgewählte Entity-Typen, z. B. "minecraft:player" oder "minecraft:zombie". */
    public List<String> entities = new ArrayList<>(List.of("minecraft:player"));

    /** 3D-Box um die Entity. */
    public boolean box = true;
    /** Box halbdurchsichtig füllen. */
    public boolean fill = false;
    /** Leuchtender Umriss (wie der Glowing-Effekt). */
    public boolean glow = false;
    /** Linie vom Fadenkreuz zur Entity. */
    public boolean tracers = false;
    /** Name über dem Kopf. */
    public boolean names = true;
    /** Entfernung im Namensschild. */
    public boolean distance = true;
    /** Leben im Namensschild (sofern der Server es mitschickt). */
    public boolean health = true;
    /** Unsichtbare Entities ebenfalls anzeigen. */
    public boolean showInvisible = true;
    public ColorMode colorMode = ColorMode.DISTANCE;
    public String fixedColor = "#FF3B3B";
    /** Maximale Entfernung in Blöcken. */
    public int range = 256;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("esp.json");
    }

    public static EspConfig load() {
        Path f = file();
        EspConfig cfg = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, EspConfig.class);
            } catch (IOException | JsonParseException e) {
                EspClient.LOGGER.warn("esp.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        if (cfg == null) cfg = new EspConfig();
        cfg.sanitize();
        return cfg;
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("esp.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            EspClient.LOGGER.warn("esp.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        if (colorMode == null) colorMode = ColorMode.DISTANCE;
        List<String> cleaned = new ArrayList<>();
        if (entities != null) {
            for (String id : entities) {
                if (id == null || id.isBlank()) continue;
                String norm = id.trim().toLowerCase(Locale.ROOT);
                if (!cleaned.contains(norm)) cleaned.add(norm);
            }
        }
        entities = cleaned;
        range = Math.max(16, Math.min(1024, range));
        if (!isValidColor(fixedColor)) fixedColor = "#FF3B3B";
        fixedColor = (fixedColor.trim().startsWith("#") ? fixedColor.trim() : "#" + fixedColor.trim()).toUpperCase(Locale.ROOT);
    }

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
}
