package com.adhdclient.basefinder.config;

import com.adhdclient.basefinder.BaseFinderClient;
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

/**
 * Komplette Konfiguration, gespeichert als config/base_finder.json (nur Gson, keine externe Abhängigkeit).
 *
 * Block-Muster (überall, wo Block-IDs stehen):
 *  - "minecraft:torch"            exakte ID (ohne Namespace wird "minecraft:" angenommen)
 *  - "minecraft:*_candle"         Platzhalter * für beliebig viele Zeichen
 *  - "#minecraft:logs"            Block-Tag
 *  - "entity:minecraft:item_frame" Objekt (Entity) statt Block, z. B. Rahmen
 */
public final class ModConfig {

    public static final String FILE_NAME = BaseFinderClient.MOD_ID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int configVersion = 2;
    public General general = new General();
    public Amethyst amethyst = new Amethyst();
    public Bees bees = new Bees();
    public Kelp kelp = new Kelp();
    public Spawners spawners = new Spawners();
    public Anomaly anomaly = new Anomaly();

    // ------------------------------------------------------------------ Abschnitte

    public static final class General {
        /** Hauptschalter "Konstantes Scannen". */
        public boolean constantScanning = true;
        /** Markierungen sichtbar (per Anzeigen-Taste umschaltbar). */
        public boolean markersVisible = true;
        /** Taste: Markierungen ein-/ausblenden (GLFW-Code oder Maustaste, -1 = keine), im Menü einstellbar. */
        public int markersKey = org.lwjgl.glfw.GLFW.GLFW_KEY_H;
        /** Taste: alle Chunks neu scannen. */
        public int rescanKey = org.lwjgl.glfw.GLFW.GLFW_KEY_J;
        /** Scan-Radius in Chunks (wird zusätzlich auf die Render-Distanz begrenzt). */
        public int scanRadius = 8;
        /** Maximale Scan-Arbeit pro Tick in Millisekunden. */
        public double scanBudgetMs = 2.0;
        /** Langsamer Hintergrund-Durchlauf über alle Chunks, in Sekunden (0 = aus). */
        public int backgroundIntervalSec = 30;
        /** Verzögerung, bevor ein geänderter ("dirty") Chunk neu gescannt wird. */
        public int dirtyDelayMs = 1500;
        /** Verzögerung nach dem Laden eines Chunks (damit die Lichtdaten angekommen sind). */
        public int loadDelayMs = 1500;
        /** Ton bei neuen Funden. */
        public boolean notificationSound = false;
        /** Grundgröße der Beschriftungen. */
        public double labelScale = 1.0;
    }

    public static final class Amethyst {
        public boolean enabled = true;
        public boolean countCluster = true;
        public boolean countLargeBud = false;
        public boolean countMediumBud = false;
        public boolean countSmallBud = false;
        public boolean countBudding = false;
        public int minCount = 15;
        public int topN = 3;
        public String color = "#B061FF";
        public boolean notify = true;
    }

    public static final class Bees {
        public boolean enabled = true;
        public boolean includeBeeNest = true;
        public boolean includeBeehive = true;
        public boolean critUnderground = true;
        public boolean critBiome = true;
        public boolean critDimension = true;
        public boolean critNoTree = true;
        public boolean beehiveAlwaysUnnatural = false;
        public boolean hideEmpty = true;
        public int topN = 3;
        /** Radius für die Baumstruktur-Suche (Blöcke). */
        public int treeRadius = 3;
        /** Mindestanzahl Stamm-/Laubblöcke im Radius (davon mindestens 1 Stamm). */
        public int minTreeBlocks = 4;
        /** Biome, in denen Nester natürlich generieren. */
        public List<String> naturalBiomes = new ArrayList<>(List.of(
                "minecraft:plains",
                "minecraft:sunflower_plains",
                "minecraft:meadow",
                "minecraft:forest",
                "minecraft:flower_forest",
                "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest",
                "minecraft:cherry_grove",
                "minecraft:mangrove_swamp"));
        public String color = "#FFD21F";
        public boolean notify = true;
    }

    public static final class Kelp {
        public boolean enabled = true;
        public int minHeight = 26;
        public int topN = 3;
        public String color = "#3BD63B";
        public boolean notify = true;
    }

    public static final class Spawners {
        public boolean enabled = true;
        /** Mob kommt nie aus natürlichen Spawnern (z. B. Schwein, Creeper). */
        public boolean critWrongMob = true;
        /** Mob passt zur Struktur, aber falsche Dimension (z. B. Lohe in der Oberwelt). */
        public boolean critDimension = true;
        /** In der Nähe fehlen die typischen Blöcke der passenden Struktur. */
        public boolean critNoStructure = true;
        /** Über dem Spawner ist freier Himmel. */
        public boolean critOpenSky = true;
        /** Spawner ohne Mob. */
        public boolean critEmpty = true;
        /** Auch Trial-Spawner prüfen (gehören in Trial Chambers). */
        public boolean includeTrialSpawners = true;
        /** Auch natürliche Spawner markieren (unnatürliche stehen immer zuerst). */
        public boolean showAll = true;
        public int topN = 10;
        /** Mob-ID -> Struktur, in der dieser Mob natürlich aus einem Spawner kommt. */
        public Map<String, String> naturalMobs = defaultSpawnerMobs();
        /** Typische Umgebung jeder Struktur. */
        public List<SpawnerContext> contexts = defaultSpawnerContexts();
        public String color = "#33E0FF";
        public boolean notify = true;
    }

    public static final class SpawnerContext {
        public String name = "";
        /** overworld, the_nether oder the_end */
        public String dimension = "overworld";
        public List<String> blocks = new ArrayList<>();
        public int minCount = 10;
        public int radius = 5;

        public SpawnerContext() {
        }

        public SpawnerContext(String name, String dimension, int minCount, int radius, String... blocks) {
            this.name = name;
            this.dimension = dimension;
            this.minCount = minCount;
            this.radius = radius;
            this.blocks = new ArrayList<>(List.of(blocks));
        }
    }

    public static final class Anomaly {
        public boolean enabled = true;
        public boolean lightSources = true;
        public boolean lightCheck = true;
        public boolean otherBlocks = true;
        public boolean structureFilter = true;
        public boolean markBase = true;
        public int minScore = 12;
        public int topN = 3;
        /** Ein Chunk mit erkannter Struktur wird trotzdem gemeldet, wenn Score >= typischer Wert * Faktor. */
        public double structureToleranceFactor = 3.0;
        /** Licht-Check: Punkte pro 10 künstlich beleuchteten Luftblöcken unter der Oberfläche. */
        public double lightCheckWeightPer10 = 1.0;
        /** Licht-Check: Obergrenze der Punkte aus beleuchteter Luft. */
        public double lightCheckMax = 30.0;
        /** Gewicht für leuchtende Blöcke, die weder natürlich noch in einer Liste sind (z. B. minecraft:light). */
        public double unknownLightWeight = 1.0;

        public List<WeightedGroup> lightGroups = defaultLightGroups();
        public List<WeightedGroup> otherGroups = defaultOtherGroups();
        public List<CustomBlock> customBlocks = new ArrayList<>();

        public List<String> naturalLightOverworld = new ArrayList<>(List.of(
                "minecraft:lava", "minecraft:magma_block", "minecraft:glow_lichen",
                "minecraft:cave_vines", "minecraft:cave_vines_plant",
                "minecraft:amethyst_cluster", "minecraft:large_amethyst_bud",
                "minecraft:medium_amethyst_bud", "minecraft:small_amethyst_bud",
                "minecraft:sculk_catalyst", "minecraft:sculk_sensor", "minecraft:brown_mushroom",
                "minecraft:crying_obsidian", "minecraft:fire", "minecraft:redstone_ore",
                "minecraft:deepslate_redstone_ore", "minecraft:sea_pickle", "minecraft:firefly_bush",
                "minecraft:end_portal_frame", "minecraft:trial_spawner", "minecraft:vault"));
        public List<String> naturalLightNether = new ArrayList<>(List.of(
                "minecraft:lava", "minecraft:magma_block", "minecraft:glowstone", "minecraft:shroomlight",
                "minecraft:fire", "minecraft:soul_fire", "minecraft:crying_obsidian",
                "minecraft:weeping_vines", "minecraft:weeping_vines_plant", "minecraft:brown_mushroom"));
        public List<String> naturalLightEnd = new ArrayList<>(List.of(
                "minecraft:end_rod", "minecraft:end_gateway", "minecraft:end_portal",
                "minecraft:fire", "minecraft:dragon_egg"));

        /** Typischer Score natürlicher Strukturen (Name -> Score). */
        public Map<String, Double> structureTypicalScore = defaultTypicalScores();

        public String color = "#FF5A1F";
        public boolean notify = true;
    }

    public static final class WeightedGroup {
        public String name;
        public List<String> blocks;
        public double weight;
        public boolean enabled = true;
        /** "" = immer zählen, "*" = nicht in erkannten Strukturen zählen, sonst Name einer Struktur. */
        public String ignoreInStructure = "";

        public WeightedGroup() {
        }

        public WeightedGroup(String name, double weight, String ignoreInStructure, String... blocks) {
            this.name = name;
            this.weight = weight;
            this.ignoreInStructure = ignoreInStructure;
            this.blocks = new ArrayList<>(List.of(blocks));
        }
    }

    public static final class CustomBlock {
        public String block = "";
        public double weight = 1.0;

        public CustomBlock() {
        }

        public CustomBlock(String block, double weight) {
            this.block = block;
            this.weight = weight;
        }
    }

    // ------------------------------------------------------------------ Standardwerte

    public static List<WeightedGroup> defaultLightGroups() {
        List<WeightedGroup> l = new ArrayList<>();
        l.add(new WeightedGroup("Fackeln", 1.0, "", "minecraft:torch", "minecraft:wall_torch"));
        l.add(new WeightedGroup("Seelenfackeln", 1.5, "", "minecraft:soul_torch", "minecraft:soul_wall_torch"));
        l.add(new WeightedGroup("Kupferfackeln", 1.5, "", "minecraft:copper_torch", "minecraft:copper_wall_torch"));
        l.add(new WeightedGroup("Redstone-Fackeln", 0.5, "", "minecraft:redstone_torch", "minecraft:redstone_wall_torch"));
        l.add(new WeightedGroup("Laternen", 1.5, "", "minecraft:lantern"));
        l.add(new WeightedGroup("Seelenlaternen", 1.5, "", "minecraft:soul_lantern"));
        l.add(new WeightedGroup("Kupferlaternen", 1.5, "", "minecraft:*copper_lantern"));
        l.add(new WeightedGroup("Lagerfeuer", 1.0, "", "minecraft:campfire"));
        l.add(new WeightedGroup("Seelenlagerfeuer", 1.0, "", "minecraft:soul_campfire"));
        l.add(new WeightedGroup("Kürbislaternen", 1.5, "", "minecraft:jack_o_lantern"));
        l.add(new WeightedGroup("Redstone-Lampen", 1.5, "", "minecraft:redstone_lamp"));
        l.add(new WeightedGroup("Endstäbe", 1.5, "", "minecraft:end_rod"));
        l.add(new WeightedGroup("Kerzen", 0.5, "", "minecraft:*candle", "minecraft:*candle_cake"));
        l.add(new WeightedGroup("Kupferleuchten", 2.0, "", "minecraft:*copper_bulb"));
        l.add(new WeightedGroup("Leuchtstein", 2.0, "", "minecraft:glowstone"));
        l.add(new WeightedGroup("Seelaternen", 2.0, "", "minecraft:sea_lantern"));
        l.add(new WeightedGroup("Pilzlicht", 2.0, "", "minecraft:shroomlight"));
        l.add(new WeightedGroup("Froschlichter", 2.0, "", "minecraft:*_froglight"));
        l.add(new WeightedGroup("Leuchtfeuer", 10.0, "", "minecraft:beacon"));
        l.add(new WeightedGroup("Aquisitoren", 8.0, "", "minecraft:conduit"));
        l.add(new WeightedGroup("Lichtblöcke", 5.0, "", "minecraft:light"));
        return l;
    }

    public static List<WeightedGroup> defaultOtherGroups() {
        List<WeightedGroup> l = new ArrayList<>();
        l.add(new WeightedGroup("Werkbänke", 1.0, "", "minecraft:crafting_table"));
        l.add(new WeightedGroup("Öfen", 1.0, "", "minecraft:furnace"));
        l.add(new WeightedGroup("Schmelzöfen", 1.0, "", "minecraft:blast_furnace"));
        l.add(new WeightedGroup("Räucheröfen", 1.0, "", "minecraft:smoker"));
        l.add(new WeightedGroup("Truhen", 0.5, "", "minecraft:chest", "minecraft:trapped_chest"));
        l.add(new WeightedGroup("Fässer", 0.5, "", "minecraft:barrel"));
        l.add(new WeightedGroup("Betten", 1.0, "", "minecraft:*_bed"));
        l.add(new WeightedGroup("Ambosse", 1.0, "", "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"));
        l.add(new WeightedGroup("Zaubertische", 2.0, "", "minecraft:enchanting_table"));
        l.add(new WeightedGroup("Schilder", 0.5, "", "minecraft:*_sign"));
        l.add(new WeightedGroup("Rahmen", 1.0, "", "entity:minecraft:item_frame", "entity:minecraft:glow_item_frame"));
        l.add(new WeightedGroup("Schienen", 0.2, "Mineshaft", "minecraft:*rail"));
        l.add(new WeightedGroup("Glas", 0.1, "", "minecraft:*glass", "minecraft:*glass_pane"));
        l.add(new WeightedGroup("Beton", 0.2, "", "minecraft:*_concrete"));
        l.add(new WeightedGroup("Wolle", 0.2, "", "minecraft:*_wool"));
        l.add(new WeightedGroup("Glasierte Keramik", 0.3, "", "minecraft:*_glazed_terracotta"));
        l.add(new WeightedGroup("Treppen", 0.05, "*", "minecraft:*_stairs"));
        l.add(new WeightedGroup("Stufen", 0.05, "*", "minecraft:*_slab"));
        return l;
    }

    public static Map<String, String> defaultSpawnerMobs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("minecraft:zombie", "Verlies");
        m.put("minecraft:skeleton", "Verlies");
        m.put("minecraft:spider", "Verlies");
        m.put("minecraft:cave_spider", "Mineshaft");
        m.put("minecraft:silverfish", "Festung");
        m.put("minecraft:blaze", "Nether-Festung");
        m.put("minecraft:magma_cube", "Bastion");
        return m;
    }

    public static List<SpawnerContext> defaultSpawnerContexts() {
        List<SpawnerContext> l = new ArrayList<>();
        l.add(new SpawnerContext("Verlies", "overworld", 12, 4,
                "minecraft:cobblestone", "minecraft:mossy_cobblestone"));
        l.add(new SpawnerContext("Mineshaft", "overworld", 4, 5,
                "minecraft:cobweb", "minecraft:*rail", "minecraft:oak_planks", "minecraft:dark_oak_planks",
                "minecraft:oak_fence", "minecraft:dark_oak_fence"));
        l.add(new SpawnerContext("Festung", "overworld", 15, 6,
                "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
                "minecraft:chiseled_stone_bricks", "minecraft:infested_*stone_bricks", "minecraft:stone_brick_stairs",
                "minecraft:end_portal_frame", "minecraft:iron_bars"));
        l.add(new SpawnerContext("Nether-Festung", "the_nether", 15, 5,
                "minecraft:nether_bricks", "minecraft:nether_brick_fence", "minecraft:nether_brick_stairs"));
        l.add(new SpawnerContext("Bastion", "the_nether", 15, 5,
                "minecraft:blackstone", "minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks",
                "minecraft:gilded_blackstone", "minecraft:basalt", "minecraft:polished_basalt", "minecraft:polished_blackstone"));
        l.add(new SpawnerContext("Trial Chambers", "overworld", 15, 6,
                "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:chiseled_tuff*", "minecraft:waxed_*copper*",
                "minecraft:tuff_brick_*", "minecraft:polished_tuff_*"));
        return l;
    }

    public static Map<String, Double> defaultTypicalScores() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Mineshaft", 6.0);
        m.put("Festung", 10.0);
        m.put("Dorf", 15.0);
        m.put("Plünderer-Außenposten", 6.0);
        m.put("Waldanwesen", 25.0);
        m.put("Wüstentempel", 4.0);
        m.put("Dschungeltempel", 4.0);
        m.put("Trial Chambers", 40.0);
        m.put("Ancient City", 25.0);
        m.put("Bastion", 10.0);
        m.put("Nether-Festung", 4.0);
        m.put("End-Stadt", 20.0);
        m.put("Ozeanmonument", 15.0);
        m.put("Verlies", 3.0);
        m.put("Iglu", 6.0);
        m.put("Hexenhütte", 4.0);
        m.put("Trail Ruins", 6.0);
        return m;
    }

    // ------------------------------------------------------------------ Laden / Speichern

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve(FILE_NAME);
    }

    public static ModConfig load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                ModConfig c = GSON.fromJson(r, ModConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                BaseFinderClient.LOGGER.warn("Config {} konnte nicht gelesen werden, Standardwerte werden benutzt", p, e);
                try {
                    Files.copy(p, p.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // Sicherungskopie ist nur ein Bonus
                }
            }
        }
        ModConfig c = new ModConfig();
        c.save();
        return c;
    }

    public void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(FILE_NAME + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BaseFinderClient.LOGGER.error("Config {} konnte nicht gespeichert werden", p, e);
        }
    }

    /** Fehlende Abschnitte (alte/handbearbeitete Dateien) ergänzen und Werte in sinnvolle Grenzen bringen. */
    public void sanitize() {
        if (general == null) general = new General();
        if (amethyst == null) amethyst = new Amethyst();
        if (bees == null) bees = new Bees();
        if (kelp == null) kelp = new Kelp();
        if (spawners == null) spawners = new Spawners();
        if (anomaly == null) anomaly = new Anomaly();

        general.scanRadius = clamp(general.scanRadius, 1, 32);
        general.scanBudgetMs = Math.max(0.2, Math.min(20.0, general.scanBudgetMs));
        general.backgroundIntervalSec = clamp(general.backgroundIntervalSec, 0, 3600);
        general.dirtyDelayMs = clamp(general.dirtyDelayMs, 100, 10000);
        general.loadDelayMs = clamp(general.loadDelayMs, 0, 10000);
        general.labelScale = Math.max(0.25, Math.min(4.0, general.labelScale));

        amethyst.minCount = clamp(amethyst.minCount, 1, 4096);
        amethyst.topN = clamp(amethyst.topN, 1, 10);
        amethyst.color = color(amethyst.color, "#B061FF");

        bees.topN = clamp(bees.topN, 1, 10);
        bees.treeRadius = clamp(bees.treeRadius, 1, 6);
        bees.minTreeBlocks = clamp(bees.minTreeBlocks, 1, 100);
        if (bees.naturalBiomes == null) bees.naturalBiomes = new Bees().naturalBiomes;
        bees.color = color(bees.color, "#FFD21F");

        kelp.minHeight = clamp(kelp.minHeight, 2, 400);
        kelp.topN = clamp(kelp.topN, 1, 10);
        kelp.color = color(kelp.color, "#3BD63B");

        if (configVersion < 2) {
            // Version 2: alle Spawner anzeigen -> mehr Markierungen als vorher
            spawners.topN = Math.max(spawners.topN, 10);
            configVersion = 2;
        }
        spawners.topN = clamp(spawners.topN, 1, 30);
        if (spawners.naturalMobs == null) spawners.naturalMobs = defaultSpawnerMobs();
        if (spawners.contexts == null) spawners.contexts = defaultSpawnerContexts();
        spawners.contexts.removeIf(c -> c == null);
        for (SpawnerContext c : spawners.contexts) {
            if (c.name == null) c.name = "";
            if (c.dimension == null) c.dimension = "overworld";
            if (c.blocks == null) c.blocks = new ArrayList<>();
            c.minCount = clamp(c.minCount, 0, 10000);
            c.radius = clamp(c.radius, 1, 8);
        }
        spawners.color = color(spawners.color, "#33E0FF");

        Anomaly a = anomaly;
        a.minScore = clamp(a.minScore, 1, 100000);
        a.topN = clamp(a.topN, 1, 10);
        a.structureToleranceFactor = Math.max(1.0, Math.min(100.0, a.structureToleranceFactor));
        a.lightCheckWeightPer10 = Math.max(0, Math.min(100, a.lightCheckWeightPer10));
        a.lightCheckMax = Math.max(0, Math.min(10000, a.lightCheckMax));
        a.unknownLightWeight = Math.max(0, Math.min(100, a.unknownLightWeight));
        if (a.lightGroups == null) a.lightGroups = defaultLightGroups();
        if (a.otherGroups == null) a.otherGroups = defaultOtherGroups();
        if (a.customBlocks == null) a.customBlocks = new ArrayList<>();
        Anomaly def = new Anomaly();
        if (a.naturalLightOverworld == null) a.naturalLightOverworld = def.naturalLightOverworld;
        if (a.naturalLightNether == null) a.naturalLightNether = def.naturalLightNether;
        if (a.naturalLightEnd == null) a.naturalLightEnd = def.naturalLightEnd;
        if (a.structureTypicalScore == null) a.structureTypicalScore = defaultTypicalScores();
        for (Map.Entry<String, Double> e : defaultTypicalScores().entrySet()) {
            a.structureTypicalScore.putIfAbsent(e.getKey(), e.getValue());
        }
        sanitizeGroups(a.lightGroups);
        sanitizeGroups(a.otherGroups);
        a.customBlocks.removeIf(cb -> cb == null);
        for (CustomBlock cb : a.customBlocks) {
            if (cb.block == null) cb.block = "";
            cb.weight = Math.max(-1000, Math.min(1000, cb.weight));
        }
        a.color = color(a.color, "#FF5A1F");
    }

    private static void sanitizeGroups(List<WeightedGroup> groups) {
        groups.removeIf(g -> g == null);
        for (WeightedGroup g : groups) {
            if (g.name == null || g.name.isBlank()) g.name = "Gruppe";
            if (g.blocks == null) g.blocks = new ArrayList<>();
            if (g.ignoreInStructure == null) g.ignoreInStructure = "";
            g.weight = Math.max(0, Math.min(1000, g.weight));
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String color(String s, String fallback) {
        return parseColor(s) == null ? fallback : s.trim().toUpperCase();
    }

    /** "#RRGGBB" -> 0xRRGGBB, sonst null. */
    public static Integer parseColor(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return null;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int rgb(String s, int fallback) {
        Integer c = parseColor(s);
        return c == null ? fallback : c;
    }
}
