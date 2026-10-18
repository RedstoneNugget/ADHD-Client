package com.adhdclient.gui;

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
import java.util.LinkedHashMap;
import java.util.Map;

/** Farben der Menüs, gespeichert als config/adhd_client/theme.json (alle als 0xRRGGBB). */
public final class ThemeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Hauptfarbe: Titelleiste, Ränder, Überschriften, Regler. */
    public int accent = 0x8B5CF6;
    /** Hintergrund der Fenster. */
    public int background = 0x14121C;
    /** Schrift. */
    public int text = 0xECEAF4;
    /** Schalter an. */
    public int on = 0x34D399;
    /** Schalter aus. */
    public int off = 0xF43F5E;
    /** Sprache der Menüs: automatisch (Spielsprache), Deutsch oder Englisch. */
    public L.Mode language = L.Mode.AUTO;
    /** Positionen der ClickGUI-Fenster: Name → {x, y, eingeklappt (0/1)}. */
    public Map<String, int[]> panels = new LinkedHashMap<>();
    /** Deckkraft des Hintergrunds in Prozent. */
    public int opacity = 92;

    /** Fertige Farbsets: Name → {Akzent, Hintergrund, Schrift, An, Aus}. */
    public static final Map<String, int[]> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Lila", new int[]{0x8B5CF6, 0x14121C, 0xECEAF4, 0x34D399, 0xF43F5E});
        PRESETS.put("Ozean", new int[]{0x38BDF8, 0x0C1622, 0xE6F1FA, 0x2DD4BF, 0xFB7185});
        PRESETS.put("Glut", new int[]{0xF97316, 0x1A120E, 0xF5EDE6, 0xA3E635, 0xEF4444});
        PRESETS.put("Mint", new int[]{0x10B981, 0x0F1A16, 0xE7F5EF, 0x22D3EE, 0xF97316});
        PRESETS.put("Rose", new int[]{0xEC4899, 0x1A1018, 0xF9E8F2, 0x4ADE80, 0xF59E0B});
        PRESETS.put("Grau", new int[]{0xA1A1AA, 0x111113, 0xF4F4F5, 0x4ADE80, 0xF87171});
    }

    public void apply(int[] preset) {
        accent = preset[0];
        background = preset[1];
        text = preset[2];
        on = preset[3];
        off = preset[4];
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("theme.json");
    }

    public static ThemeConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                ThemeConfig c = GSON.fromJson(r, ThemeConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                AdhdClientClient.LOGGER.warn("theme.json ist fehlerhaft – Standardfarben werden verwendet ({})", e.getMessage());
            }
        }
        return new ThemeConfig();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("theme.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AdhdClientClient.LOGGER.warn("theme.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        accent &= 0xFFFFFF;
        background &= 0xFFFFFF;
        text &= 0xFFFFFF;
        on &= 0xFFFFFF;
        off &= 0xFFFFFF;
        opacity = Math.max(40, Math.min(100, opacity));
        if (language == null) language = L.Mode.AUTO;
        if (panels == null) panels = new LinkedHashMap<>();
    }
}
