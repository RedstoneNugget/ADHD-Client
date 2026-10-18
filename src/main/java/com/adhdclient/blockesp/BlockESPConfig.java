package com.adhdclient.blockesp;

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

/** Einstellungen von Block ESP, gespeichert als config/adhd_client/block_esp.json. */
public final class BlockESPConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Ausgewählte Block-IDs, z. B. "minecraft:diamond_ore". */
    public List<String> selectedBlocks = new ArrayList<>(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
    /** Maximale Entfernung der Markierungen in Blöcken. */
    public int range = 128;
    /** Boxen halbdurchsichtig füllen (sonst nur Umriss). */
    public boolean fill = true;
    /** Linien vom Fadenkreuz zu den Blöcken. */
    public boolean tracers = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("block_esp.json");
    }

    public static BlockESPConfig load() {
        Path f = file();
        BlockESPConfig cfg = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, BlockESPConfig.class);
            } catch (IOException | JsonParseException e) {
                BlockESPClient.LOGGER.warn("block_esp.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        if (cfg == null) cfg = new BlockESPConfig();
        cfg.sanitize();
        return cfg;
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("block_esp.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BlockESPClient.LOGGER.warn("block_esp.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        range = Math.max(16, Math.min(512, range));
        List<String> cleaned = new ArrayList<>();
        if (selectedBlocks != null) {
            for (String id : selectedBlocks) {
                if (id == null || id.isBlank()) continue;
                String norm = id.trim().toLowerCase(Locale.ROOT);
                if (!cleaned.contains(norm)) cleaned.add(norm);
            }
        }
        selectedBlocks = cleaned;
    }
}
