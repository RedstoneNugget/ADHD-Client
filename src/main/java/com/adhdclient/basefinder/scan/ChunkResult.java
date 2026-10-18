package com.adhdclient.basefinder.scan;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Rohdaten eines gescannten Chunks. Bewusst ohne Config-abhängige Bewertung: Schwellen, Gewichte,
 * Top-N und Kriterien werden erst bei der Auswertung angewendet, sodass Änderungen im Menü
 * meist ohne Neuscan sofort wirken.
 */
public final class ChunkResult {

    public final int cx;
    public final int cz;
    public final long key;
    public final long scannedAt;

    /** Anzahl der "interessanten" Blöcke im Chunk. */
    public final Reference2IntOpenHashMap<Block> counts = new Reference2IntOpenHashMap<>();
    /** Anzahl leuchtender Zustände (Leuchtkraft > 0) pro Block. */
    public final Reference2IntOpenHashMap<Block> emitterCounts = new Reference2IntOpenHashMap<>();
    /** Bitmaske der Sektionen (Index = Sektion ab Weltboden, max. 64), in denen ein Block vorkommt. */
    public final Reference2LongOpenHashMap<Block> sectionMask = new Reference2LongOpenHashMap<>();
    public final Object2IntOpenHashMap<EntityType<?>> entityCounts = new Object2IntOpenHashMap<>();

    /** Pro Amethyst-Typ niedrigste/höchste Y-Koordinate. */
    public final int[] amethystMinY = new int[Compiled.AMETHYST_TYPES.length];
    public final int[] amethystMaxY = new int[Compiled.AMETHYST_TYPES.length];

    public final List<NestFact> nests = new ArrayList<>();
    public final List<KelpColumn> kelp = new ArrayList<>();
    public final List<SpawnerFact> spawners = new ArrayList<>();

    public boolean lightChecked;
    /** Luftblöcke unter der Oberfläche mit Blocklicht, das keine natürliche Quelle erklärt. */
    public int litArtificialAir;
    public long litSectionMask;

    /** Stärke je Struktur-Signatur (Index wie StructureSignatures.ALL). */
    public double[] structureStrength = new double[0];

    public int bottomY;
    public int sectionCount;

    public ChunkResult(int cx, int cz, long scannedAt) {
        this.cx = cx;
        this.cz = cz;
        this.key = ChunkPos.toLong(cx, cz);
        this.scannedAt = scannedAt;
        java.util.Arrays.fill(amethystMinY, Integer.MAX_VALUE);
        java.util.Arrays.fill(amethystMaxY, Integer.MIN_VALUE);
    }

    public int count(Block b) {
        return counts.getInt(b);
    }

    public static final class NestFact {
        public final BlockPos pos;
        public final boolean beehive;
        public final int honey;
        public final boolean underground;
        public final String biome;
        public final int logs;
        public final int leaves;
        /** Bienenanzahl laut Client-Block-Entity, -1 = unbekannt. */
        public final int clientBees;

        public NestFact(BlockPos pos, boolean beehive, int honey, boolean underground, String biome, int logs, int leaves, int clientBees) {
            this.pos = pos;
            this.beehive = beehive;
            this.honey = honey;
            this.underground = underground;
            this.biome = biome;
            this.logs = logs;
            this.leaves = leaves;
            this.clientBees = clientBees;
        }
    }

    public record KelpColumn(BlockPos top, int height) {
    }

    /**
     * Ein Spawner mit seinem Umfeld.
     * @param mob           Mob-ID (z. B. "minecraft:zombie"), null = kein Mob, "?" = unbekannt
     * @param contextCounts Anzahl typischer Blöcke je Struktur-Umgebung (Index wie Compiled.spawnerContexts)
     */
    public record SpawnerFact(BlockPos pos, boolean trial, String mob, boolean openSky, int[] contextCounts) {
    }
}
