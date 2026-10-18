package com.adhdclient.inspect.config;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Einstellungen von Inspect All, gespeichert als config/adhd_client/inspect.json.
 * Hintergrund, Rahmen und Text kommen aus dem Design-Menü des ADHD Client; hier stehen nur die
 * Farben für die Daten (Schlüssel, Zahlen, Strings …).
 */
public final class InspectConfig {

    public enum PanelPosition {
        LEFT("Links", "Left"), CENTER("Mitte", "Center"), RIGHT("Rechts", "Right");

        private final String de;
        private final String en;

        PanelPosition(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String label() {
            return L.t(de, en);
        }

        public PanelPosition next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InspectConfig instance = new InspectConfig();

    // ---- Tasten (GLFW-Code oder Maustaste, -1 = keine), im Modul-Menü einstellbar
    public int openKey = org.lwjgl.glfw.GLFW.GLFW_KEY_I;
    public int lockKey = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    public int clearKey = HotKey.NONE;

    // ---- Panel
    public PanelPosition panelPosition = PanelPosition.CENTER;
    public int panelWidthPercent = 72;

    // ---- Farben für die Daten (#RRGGBB)
    public String colorKey = "#7CC4FF";
    public String colorString = "#98E07E";
    public String colorNumber = "#FFAE5E";
    public String colorBoolean = "#D58CFF";
    public String colorCoord = "#FFE14D";
    public String colorWarn = "#FF7A5C";
    public String colorMarker = "#FFC21A";

    // ---- Kategorien
    public Map<String, Boolean> categories = new LinkedHashMap<>();

    // ---- Sonstiges
    public boolean tooltipHint = true;
    public boolean hudWhenLocked = true;
    /** Mehrspieler mit OP-Rechten: volle NBT über die Vanilla-Abfrage (wie F3+I) holen. */
    public boolean opQuery = true;
    public int lookRange = 64;
    public int maxSnbtLines = 50000;

    public static InspectConfig get() {
        return instance;
    }

    public boolean isVisible(Category category) {
        return categories.getOrDefault(category.name(), true);
    }

    public void setVisible(Category category, boolean visible) {
        categories.put(category.name(), visible);
    }

    public int color(Role role) {
        return switch (role) {
            case TEXT -> Theme.text();
            case KEY -> parse(colorKey, 0xFF7CC4FF);
            case STRING -> parse(colorString, 0xFF98E07E);
            case NUMBER -> parse(colorNumber, 0xFFFFAE5E);
            case BOOLEAN -> parse(colorBoolean, 0xFFD58CFF);
            case PUNCT, DIM -> Theme.textDim();
            case WARN -> parse(colorWarn, 0xFFFF7A5C);
            case ACCENT -> accent();
            case COORD -> parse(colorCoord, 0xFFFFE14D);
        };
    }

    /** Akzentfarbe aus dem Design-Menü. */
    public int accent() {
        return Theme.accent();
    }

    public int markerColor() {
        return parse(colorMarker, 0xFFFFC21A);
    }

    /** Panel-Hintergrund: Farbe und Deckkraft aus dem Design-Menü. */
    public int background() {
        return Theme.argb(Math.round(Math.max(60, Theme.config().opacity) * 2.55f), Theme.config().background);
    }

    /** "#RRGGBB" oder "#AARRGGBB" → ARGB; bei Fehler der Standardwert. */
    public static int parse(String hex, int fallback) {
        Integer v = tryParse(hex);
        return v != null ? v : fallback;
    }

    public static Integer tryParse(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.toLowerCase(Locale.ROOT).startsWith("0x")) {
            s = s.substring(2);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------ Laden/Speichern

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("inspect.json");
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                InspectConfig loaded = GSON.fromJson(reader, InspectConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                InspectClient.LOGGER.warn("inspect.json unlesbar, nutze Standardwerte", e);
                instance = new InspectConfig();
            }
        }
        instance.sanitize();
        save();
    }

    public static void resetToDefaults() {
        InspectConfig fresh = new InspectConfig();
        // Tasten bleiben, wie sie eingestellt sind
        fresh.openKey = instance.openKey;
        fresh.lockKey = instance.lockKey;
        fresh.clearKey = instance.clearKey;
        instance = fresh;
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            InspectClient.LOGGER.warn("inspect.json konnte nicht gespeichert werden", e);
        }
    }

    private void sanitize() {
        if (panelPosition == null) {
            panelPosition = PanelPosition.CENTER;
        }
        if (categories == null) {
            categories = new LinkedHashMap<>();
        }
        for (Category c : Category.values()) {
            categories.putIfAbsent(c.name(), true);
        }
        panelWidthPercent = Math.max(30, Math.min(100, panelWidthPercent));
        lookRange = Math.max(4, Math.min(256, lookRange));
        maxSnbtLines = Math.max(1000, Math.min(500000, maxSnbtLines));
        InspectConfig defaults = new InspectConfig();
        if (tryParse(colorKey) == null) colorKey = defaults.colorKey;
        if (tryParse(colorString) == null) colorString = defaults.colorString;
        if (tryParse(colorNumber) == null) colorNumber = defaults.colorNumber;
        if (tryParse(colorBoolean) == null) colorBoolean = defaults.colorBoolean;
        if (tryParse(colorCoord) == null) colorCoord = defaults.colorCoord;
        if (tryParse(colorWarn) == null) colorWarn = defaults.colorWarn;
        if (tryParse(colorMarker) == null) colorMarker = defaults.colorMarker;
    }
}
