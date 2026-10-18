package com.adhdclient.basefinder.scan;

import com.adhdclient.basefinder.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Aus der Config "kompilierte" Nachschlagetabellen. Werden auf dem Client-Thread gebaut, wenn
 * die Welt betreten wird, sich Tags ändern oder die Config geändert wird – damit der Scan selbst
 * nur noch Array-Zugriffe statt String-Vergleiche macht.
 */
public final class Compiled {

    public static final Block[] AMETHYST_TYPES = {
            Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.SMALL_AMETHYST_BUD, Blocks.BUDDING_AMETHYST};
    public static final String[] AMETHYST_NAMES = {"Cluster", "große Knospen", "mittlere Knospen", "kleine Knospen", "Knospender Amethyst"};

    public static final class Group {
        public final String name;
        public final Set<Block> blocks;
        public final Set<EntityType<?>> entities;
        public final double weight;
        public final boolean enabled;
        public final String ignoreInStructure;

        Group(String name, Set<Block> blocks, Set<EntityType<?>> entities, double weight, boolean enabled, String ignoreInStructure) {
            this.name = name;
            this.blocks = blocks;
            this.entities = entities;
            this.weight = weight;
            this.enabled = enabled;
            this.ignoreInStructure = ignoreInStructure == null ? "" : ignoreInStructure;
        }
    }

    /** Typische Umgebung einer Struktur für die Spawner-Prüfung. */
    public record SpawnerCtx(String name, String dimension, Set<Block> blocks, int minCount, int radius) {
    }

    public final List<SpawnerCtx> spawnerContexts = new ArrayList<>();
    public final int spawnerMaxRadius;

    // Welche Modi gescannt werden
    public final boolean amethystOn;
    public final boolean beesOn;
    public final boolean kelpOn;
    public final boolean spawnersOn;
    public final boolean anomalyOn;
    public final boolean lightCheckOn;
    public final int treeRadius;

    /** Blöcke, deren Anzahl pro Chunk gezählt wird (Index = Roh-ID des Blocks). */
    final boolean[] interesting;
    /** 1..5 = Amethyst-Typ (Index in AMETHYST_TYPES + 1), 0 = keiner. */
    final byte[] amethystType;
    final boolean[] naturalOverworld;
    final boolean[] naturalNether;
    final boolean[] naturalEnd;
    /** Leuchtende Blöcke mitzählen (Licht-Check / unbekannte Lichtquellen). */
    final boolean trackEmitters;

    public final List<Group> lightGroups = new ArrayList<>();
    public final List<Group> otherGroups = new ArrayList<>();
    public final List<Group> customGroups = new ArrayList<>();
    /** Blöcke, die in irgendeiner Liste stehen (für "unbekannte Lichtquelle"). */
    public final Set<Block> listedBlocks = new LinkedHashSet<>();
    public final Set<EntityType<?>> countedEntities = new LinkedHashSet<>();
    public final Set<Block> naturalOverworldSet;
    public final Set<Block> naturalNetherSet;
    public final Set<Block> naturalEndSet;

    /** Ändert sich dieser Wert, müssen alle Chunks neu gescannt werden. */
    public final String scanSignature;

    final Predicate<BlockState> relevant;

    public Compiled(ModConfig cfg) {
        amethystOn = cfg.amethyst.enabled;
        beesOn = cfg.bees.enabled;
        kelpOn = cfg.kelp.enabled;
        spawnersOn = cfg.spawners.enabled;
        anomalyOn = cfg.anomaly.enabled;
        lightCheckOn = anomalyOn && cfg.anomaly.lightCheck;
        trackEmitters = anomalyOn;
        treeRadius = cfg.bees.treeRadius;

        int size = Registries.BLOCK.size();
        interesting = new boolean[size];
        amethystType = new byte[size];

        StructureSignatures.resolve();

        if (amethystOn) {
            for (int i = 0; i < AMETHYST_TYPES.length; i++) {
                int id = Registries.BLOCK.getRawId(AMETHYST_TYPES[i]);
                amethystType[id] = (byte) (i + 1);
                interesting[id] = true;
            }
        }
        if (beesOn) {
            mark(Blocks.BEE_NEST);
            mark(Blocks.BEEHIVE);
        }
        if (spawnersOn) {
            mark(Blocks.SPAWNER);
            if (cfg.spawners.includeTrialSpawners) mark(Blocks.TRIAL_SPAWNER);
            int maxR = 1;
            for (ModConfig.SpawnerContext c : cfg.spawners.contexts) {
                spawnerContexts.add(new SpawnerCtx(c.name, c.dimension, Patterns.blocks(c.blocks), c.minCount, c.radius));
                maxR = Math.max(maxR, c.radius);
            }
            spawnerMaxRadius = maxR;
        } else {
            spawnerMaxRadius = 0;
        }
        if (kelpOn) {
            mark(Blocks.KELP);
            mark(Blocks.KELP_PLANT);
        }

        ModConfig.Anomaly a = cfg.anomaly;
        for (ModConfig.WeightedGroup g : a.lightGroups) lightGroups.add(compile(g));
        for (ModConfig.WeightedGroup g : a.otherGroups) otherGroups.add(compile(g));
        for (ModConfig.CustomBlock cb : a.customBlocks) {
            List<String> p = List.of(cb.block);
            customGroups.add(new Group(cb.block, Patterns.blocks(p), Patterns.entities(p), cb.weight, true, ""));
        }
        naturalOverworldSet = Patterns.blocks(a.naturalLightOverworld);
        naturalNetherSet = Patterns.blocks(a.naturalLightNether);
        naturalEndSet = Patterns.blocks(a.naturalLightEnd);
        naturalOverworld = toArray(naturalOverworldSet, size);
        naturalNether = toArray(naturalNetherSet, size);
        naturalEnd = toArray(naturalEndSet, size);

        if (anomalyOn) {
            for (List<Group> list : List.of(lightGroups, otherGroups, customGroups)) {
                for (Group g : list) {
                    listedBlocks.addAll(g.blocks);
                    countedEntities.addAll(g.entities);
                }
            }
            for (Block b : listedBlocks) mark(b);
            for (Block b : StructureSignatures.allBlocks()) mark(b);
        }

        StringBuilder sig = new StringBuilder();
        sig.append(treeRadius).append(amethystOn).append(beesOn).append(spawnersOn).append(kelpOn).append(anomalyOn).append(lightCheckOn).append('|');
        for (int i = 0; i < size; i++) {
            if (interesting[i]) sig.append(i).append(',');
        }
        sig.append('|').append(countedEntities.size()).append(':');
        for (EntityType<?> t : countedEntities) sig.append(Registries.ENTITY_TYPE.getRawId(t)).append(',');
        if (lightCheckOn) {
            sig.append('|').append(naturalOverworldSet.hashCode()).append(naturalNetherSet.hashCode()).append(naturalEndSet.hashCode());
        }
        for (SpawnerCtx c : spawnerContexts) sig.append('|').append(c.name()).append(c.radius()).append(c.blocks().hashCode());
        scanSignature = sig.toString();

        final boolean[] inter = interesting;
        final boolean emit = trackEmitters;
        relevant = s -> inter[Registries.BLOCK.getRawId(s.getBlock())] || (emit && s.getLuminance() > 0);
    }

    private Group compile(ModConfig.WeightedGroup g) {
        return new Group(g.name, Patterns.blocks(g.blocks), Patterns.entities(g.blocks), g.weight, g.enabled, g.ignoreInStructure);
    }

    private void mark(Block b) {
        interesting[Registries.BLOCK.getRawId(b)] = true;
    }

    private static boolean[] toArray(Set<Block> set, int size) {
        boolean[] arr = new boolean[size];
        for (Block b : set) arr[Registries.BLOCK.getRawId(b)] = true;
        return arr;
    }

    /** Kann eine Änderung dieses Zustands ein Ergebnis beeinflussen? */
    public boolean isRelevant(BlockState state) {
        return relevant.test(state);
    }

    public boolean anyModeOn() {
        return amethystOn || beesOn || kelpOn || anomalyOn || spawnersOn;
    }

    boolean[] naturalFor(RegistryKey<World> dim) {
        if (World.NETHER.equals(dim)) return naturalNether;
        if (World.END.equals(dim)) return naturalEnd;
        return naturalOverworld;
    }

    public Set<Block> naturalSetFor(RegistryKey<World> dim) {
        if (World.NETHER.equals(dim)) return naturalNetherSet;
        if (World.END.equals(dim)) return naturalEndSet;
        return naturalOverworldSet;
    }
}
