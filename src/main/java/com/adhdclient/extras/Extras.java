package com.adhdclient.extras;

import com.adhdclient.core.AdhdClientClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Einstellungen der kleineren Module (Trajectories, Storage ESP, Nametags+, Auto Tool, …) in einer Datei:
 * config/adhd_client/extras.json. Jedes Modul hat sein eigenes Unterobjekt mit {@code enabled}.
 */
public final class Extras {

    /** Gemeinsame Basis: an/aus. Zurücksetzen lässt {@code enabled} in Ruhe. */
    public abstract static class Settings {
        public boolean enabled = false;
    }

    public static final class Trajectories extends Settings {
        public int color = 0x55FFFF;
        public boolean showHit = true;
        public boolean otherPlayers = false;
    }

    public static final class StorageEsp extends Settings {
        public boolean chests = true;
        public boolean shulkers = true;
        public boolean barrels = true;
        public boolean enderChests = true;
        public boolean others = false;
        public boolean tracers = false;
        public boolean fill = true;
        public int range = 128;
    }

    public static final class Nametags extends Settings {
        public double scale = 1.0;
        public boolean health = true;
        public boolean armor = true;
        public boolean heldItem = true;
        public boolean distance = true;
        public boolean playersOnly = true;
    }

    public static final class NewChunks extends Settings {
        public boolean showNew = true;
        public boolean showOld = false;
        public int range = 16;
        public int newColor = 0xFF3030;
        public int oldColor = 0x30FF60;
    }

    public static final class ItemEsp extends Settings {
        public boolean names = true;
        public boolean tracers = false;
        public int range = 64;
        public int color = 0xFFD040;
    }

    public static final class AutoTool extends Settings {
        public boolean switchBack = true;
        public boolean saveTools = true;
    }

    public static final class Sorter extends Settings {
        public boolean containers = true;
        public int key = GLFW.GLFW_KEY_UNKNOWN;
    }

    public static final class ChestSteal extends Settings {
        public boolean auto = false;
        public boolean closeAfter = true;
    }

    public static final class AutoEat extends Settings {
        public int hunger = 14;
        public boolean healthToo = true;
        public int health = 10;
        public boolean avoidBad = true;
    }

    public static final class Durability extends Settings {
        public int percent = 10;
        public boolean protect = false;
        public boolean sound = true;
        public boolean armor = true;
    }

    public static final class Radar extends Settings {
        public int maxPlayers = 8;
        public int range = 256;
        public boolean right = false;
        public boolean coords = false;
    }

    public static final class AutoFish extends Settings {
        public int recastDelay = 15;
        public boolean recast = true;
    }

    public static final class SafeWalk extends Settings {
        public boolean onlyBlocks = false;
    }

    public static final class NoFall extends Settings {
        public double minFall = 2.5;
    }

    public Trajectories trajectories = new Trajectories();
    public StorageEsp storageEsp = new StorageEsp();
    public Nametags nametags = new Nametags();
    public NewChunks newChunks = new NewChunks();
    public ItemEsp itemEsp = new ItemEsp();
    public AutoTool autoTool = new AutoTool();
    public Sorter sorter = new Sorter();
    public ChestSteal chestSteal = new ChestSteal();
    public AutoEat autoEat = new AutoEat();
    public Durability durability = new Durability();
    public Radar radar = new Radar();
    public AutoFish autoFish = new AutoFish();
    public SafeWalk safeWalk = new SafeWalk();
    public NoFall noFall = new NoFall();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Extras instance;

    public static Extras get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("extras.json");
    }

    private static Extras load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Extras e = GSON.fromJson(r, Extras.class);
                if (e != null) return e;
            } catch (IOException | JsonParseException ex) {
                AdhdClientClient.LOGGER.warn("extras.json ist fehlerhaft – Standardwerte werden verwendet ({})", ex.getMessage());
            }
        }
        return new Extras();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("extras.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(get(), w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AdhdClientClient.LOGGER.warn("extras.json konnte nicht gespeichert werden", e);
        }
    }
}
