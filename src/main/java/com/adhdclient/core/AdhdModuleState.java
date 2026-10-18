package com.adhdclient.core;

import com.adhdclient.basefinder.BaseFinderClient;
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

/** Welche Module (Base Finder, Head Finder, …) an sind – config/adhd_client/modules.json. */
public final class AdhdModuleState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        boolean baseFinderEnabled = true;
        boolean headFinderEnabled = true;
    }

    private AdhdModuleState() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("modules.json");
    }

    public static void load() {
        Path f = file();
        Data d = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                d = GSON.fromJson(r, Data.class);
            } catch (IOException | JsonParseException ignored) {
                // Standardwerte reichen
            }
        }
        if (d == null) d = new Data();
        BaseFinderClient.enabled = d.baseFinderEnabled;
        HeadFinderClient.enabled = d.headFinderEnabled;
    }

    public static void save() {
        Data d = new Data();
        d.baseFinderEnabled = BaseFinderClient.enabled;
        d.headFinderEnabled = HeadFinderClient.enabled;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("modules.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(d, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // nicht kritisch
        }
    }
}
