package com.adhdclient.butterfly;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Einstellungen des Butterfly-Moduls, gespeichert als config/adhd_client/butterfly.json. */
public final class ButterflyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Taste für die Inspect-Animation (GLFW-Code oder Maustaste, -1 = keine). */
    public int inspectKey = org.lwjgl.glfw.GLFW.GLFW_KEY_R;
    /** Welche Items zu welchem Look werden: Look-ID → Item-IDs. */
    public Map<String, List<String>> looks = defaultLooks();
    /** Tempo der Animationen (0,5–2). */
    public double speed = 1.0;
    public boolean equipFlip = true;
    public boolean attackFlip = true;
    public boolean idleFidget = true;

    static Map<String, List<String>> defaultLooks() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(ButterflyLook.BUTTERFLY.id, new ArrayList<>(List.of(
                "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:copper_sword", "minecraft:iron_sword",
                "minecraft:golden_sword", "minecraft:diamond_sword", "minecraft:netherite_sword")));
        return m;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("butterfly.json");
    }

    public static ButterflyConfig load() {
        Path f = file();
        ButterflyConfig c = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                c = GSON.fromJson(r, ButterflyConfig.class);
            } catch (IOException | JsonParseException e) {
                ButterflyClient.LOGGER.warn("butterfly.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        if (c == null) c = new ButterflyConfig();
        c.sanitize();
        return c;
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("butterfly.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            ButterflyClient.LOGGER.warn("butterfly.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        if (looks == null) looks = defaultLooks();
        for (ButterflyLook look : ButterflyLook.values()) looks.computeIfAbsent(look.id, k -> new ArrayList<>());
        speed = Math.max(0.5, Math.min(2.0, speed));
    }

    /** Welcher Look gilt für dieses Item (oder null)? */
    public ButterflyLook lookFor(String itemId) {
        for (ButterflyLook look : ButterflyLook.values()) {
            List<String> items = looks.get(look.id);
            if (items != null && items.contains(itemId)) return look;
        }
        return null;
    }

}
