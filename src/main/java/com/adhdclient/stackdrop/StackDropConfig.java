package com.adhdclient.stackdrop;

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

/** Einstellungen von Stack Drop, gespeichert als config/adhd_client/stack_drop.json. */
public final class StackDropConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Ausgewählte Item-/Block-IDs, z. B. "minecraft:cobblestone". */
    public List<String> selectedItems = new ArrayList<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("stack_drop.json");
    }

    public static StackDropConfig load() {
        Path f = file();
        StackDropConfig cfg = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, StackDropConfig.class);
            } catch (IOException | JsonParseException e) {
                StackDropClient.LOGGER.warn("stack_drop.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        if (cfg == null) cfg = new StackDropConfig();
        cfg.sanitize();
        return cfg;
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("stack_drop.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            StackDropClient.LOGGER.warn("stack_drop.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        List<String> cleaned = new ArrayList<>();
        if (selectedItems != null) {
            for (String id : selectedItems) {
                if (id == null || id.isBlank()) continue;
                String norm = id.trim().toLowerCase(Locale.ROOT);
                if (!cleaned.contains(norm)) cleaned.add(norm);
            }
        }
        selectedItems = cleaned;
    }
}
