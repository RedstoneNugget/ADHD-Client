package com.adhdclient.basefinder.scan;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

import java.util.ArrayList;
import java.util.List;

/**
 * Scannt einen Chunk in vielen kleinen Schritten (meist eine Sektion pro Schritt), damit der
 * ScanManager nach jedem Schritt das Zeitbudget prüfen und die Arbeit auf mehrere Ticks
 * verteilen kann. Läuft ausschließlich auf dem Client-Thread und liest nur Daten, die der
 * Client ohnehin hat (Blöcke, Heightmaps, Licht, Block-Entities, Objekte).
 */
public final class ChunkScanJob {

    private enum Phase { INIT, SECTIONS, ENTITIES, NESTS, SPAWNERS, KELP, LIGHT_PREP, LIGHT_X, LIGHT_Z, LIGHT_Y, LIGHT_JUDGE, DONE }

    private static final int MAX_KELP_PER_CHUNK = 10;

    /** Gemeinsamer Puffer für das natürliche Licht-Feld (es läuft immer nur ein Job). */
    private static byte[] lightField = new byte[0];

    final ClientWorld world;
    final WorldChunk chunk;
    final Compiled comp;
    final ChunkResult res;
    private final boolean[] natural;
    private final int bottomY;
    private final int height;
    private final ChunkSection[] sections;

    private Phase phase = Phase.INIT;
    private int sectionIdx;
    private final int[] topNoLeaves = new int[256];
    private final int[] topSurface = new int[256];

    private final List<BlockPos> nestPositions = new ArrayList<>();
    private final List<BlockPos> spawnerPositions = new ArrayList<>();
    private int spawnerIdx;
    private int nestIdx;
    private final LongArrayList kelpHeads = new LongArrayList();
    private int kelpIdx;
    private final List<ChunkResult.KelpColumn> kelpFound = new ArrayList<>();

    private boolean naturalFilled;
    private final IntArrayList lightSections = new IntArrayList();
    private int lightMinY;
    private int lightMaxY;
    private int judgeIdx;

    private boolean aborted;

    public ChunkScanJob(ClientWorld world, WorldChunk chunk, Compiled comp, long now) {
        this.world = world;
        this.chunk = chunk;
        this.comp = comp;
        this.res = new ChunkResult(chunk.getPos().x, chunk.getPos().z, now);
        this.natural = comp.naturalFor(world.getRegistryKey());
        this.bottomY = chunk.getBottomY();
        this.height = chunk.getHeight();
        this.sections = chunk.getSectionArray();
        res.bottomY = bottomY;
        res.sectionCount = sections.length;
    }

    public long key() {
        return res.key;
    }

    public boolean aborted() {
        return aborted;
    }

    public void abort() {
        aborted = true;
    }

    public ChunkResult result() {
        return res;
    }

    /** Ein kleiner Arbeitsschritt. Gibt true zurück, wenn der Job fertig (oder abgebrochen) ist. */
    // ---- Zeitstatistik pro Phase (für Leistungstests)
    private static final long[] STAT_NANOS = new long[Phase.values().length];
    private static final long[] STAT_MAX = new long[Phase.values().length];
    private static final long[] STAT_COUNT = new long[Phase.values().length];

    private static void record(Phase p, long nanos) {
        int i = p.ordinal();
        STAT_NANOS[i] += nanos;
        STAT_COUNT[i]++;
        if (nanos > STAT_MAX[i]) STAT_MAX[i] = nanos;
    }

    /** Gesamt-/Maximaldauer je Phase seit dem letzten Aufruf; setzt die Statistik zurück. */
    public static String takePhaseStats() {
        StringBuilder sb = new StringBuilder();
        for (Phase p : Phase.values()) {
            int i = p.ordinal();
            if (STAT_COUNT[i] == 0) continue;
            sb.append(String.format(java.util.Locale.ROOT, "%s: %d x, gesamt %.1f ms, max %.2f ms; ", p, STAT_COUNT[i], STAT_NANOS[i] / 1e6, STAT_MAX[i] / 1e6));
            STAT_NANOS[i] = 0;
            STAT_MAX[i] = 0;
            STAT_COUNT[i] = 0;
        }
        return sb.toString();
    }

    public boolean step() {
        if (aborted) return true;
        if (world.getChunkManager().getWorldChunk(res.cx, res.cz) != chunk) {
            aborted = true;
            return true;
        }
        Phase ran = phase;
        long t0 = System.nanoTime();
        switch (phase) {
            case INIT -> init();
            case SECTIONS -> scanSection();
            case ENTITIES -> scanEntities();
            case NESTS -> analyseNest();
            case SPAWNERS -> analyseSpawner();
            case KELP -> measureKelp();
            case LIGHT_PREP -> lightPrep();
            case LIGHT_X -> lightPassX();
            case LIGHT_Z -> lightPassZ();
            case LIGHT_Y -> lightPassY();
            case LIGHT_JUDGE -> lightJudge();
            case DONE -> {
                return true;
            }
        }
        if (phase == Phase.DONE) finish();
        record(ran, System.nanoTime() - t0);
        return phase == Phase.DONE;
    }

    // ------------------------------------------------------------------ Phasen

    private void init() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                topNoLeaves[z * 16 + x] = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                topSurface[z * 16 + x] = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
            }
        }
        if (comp.lightCheckOn) {
            int size = 256 * height;
            if (lightField.length < size) lightField = new byte[size];
            java.util.Arrays.fill(lightField, 0, size, (byte) 0);
        }
        phase = sections.length > 0 ? Phase.SECTIONS : Phase.ENTITIES;
    }

    private void scanSection() {
        int i = sectionIdx++;
        if (sectionIdx >= sections.length) phase = Phase.ENTITIES;

        ChunkSection sec = sections[i];
        if (sec == null || sec.isEmpty()) return;
        PalettedContainer<BlockState> pc = sec.getBlockStateContainer();
        // Palette-Vorprüfung: Sektionen ohne relevante Blöcke werden gar nicht erst gezählt.
        if (!pc.hasAny(comp.relevant)) return;

        final long bit = 1L << Math.min(i, 63);
        final boolean[] needs = new boolean[5]; // 0 Amethyst-Positionen, 1 Kelp, 2 Nester, 3 natürliche Lichtquellen, 4 Spawner
        pc.count((state, n) -> {
            Block b = state.getBlock();
            int id = Registries.BLOCK.getRawId(b);
            if (comp.interesting[id]) {
                res.counts.addTo(b, n);
                res.sectionMask.put(b, res.sectionMask.getLong(b) | bit);
                if (comp.amethystType[id] != 0) needs[0] = true;
                if (b == Blocks.KELP) needs[1] = true;
                if (b == Blocks.BEE_NEST || b == Blocks.BEEHIVE) needs[2] = true;
                if (comp.spawnersOn && (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER)) needs[4] = true;
            }
            if (comp.trackEmitters && state.getLuminance() > 0) {
                res.emitterCounts.addTo(b, n);
                res.sectionMask.put(b, res.sectionMask.getLong(b) | bit);
                if (comp.lightCheckOn && natural[id]) needs[3] = true;
            }
        });
        if (!(needs[0] || needs[1] || needs[2] || needs[3] || needs[4])) return;

        // Nur wenn nötig: Block für Block durch die Sektion.
        int baseY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
        int baseX = res.cx << 4;
        int baseZ = res.cz << 4;
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState s = sec.getBlockState(x, y, z);
                    if (s.isAir()) continue;
                    Block b = s.getBlock();
                    if (needs[0]) {
                        int t = comp.amethystType[Registries.BLOCK.getRawId(b)];
                        if (t != 0) {
                            res.amethystMinY[t - 1] = Math.min(res.amethystMinY[t - 1], wy);
                            res.amethystMaxY[t - 1] = Math.max(res.amethystMaxY[t - 1], wy);
                        }
                    }
                    if (needs[1] && b == Blocks.KELP) {
                        kelpHeads.add(BlockPos.asLong(baseX + x, wy, baseZ + z));
                    }
                    if (needs[4] && (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER)) {
                        spawnerPositions.add(new BlockPos(baseX + x, wy, baseZ + z));
                    }
                    if (needs[2] && (b == Blocks.BEE_NEST || b == Blocks.BEEHIVE)) {
                        nestPositions.add(new BlockPos(baseX + x, wy, baseZ + z));
                    }
                    if (needs[3]) {
                        int lum = s.getLuminance();
                        if (lum > 0 && natural[Registries.BLOCK.getRawId(b)]) {
                            int idx = (wy - bottomY) * 256 + z * 16 + x;
                            if (lum > lightField[idx]) lightField[idx] = (byte) lum;
                            naturalFilled = true;
                        }
                    }
                }
            }
        }
    }

    private void scanEntities() {
        if (comp.anomalyOn && !comp.countedEntities.isEmpty()) {
            int x0 = res.cx << 4;
            int z0 = res.cz << 4;
            Box box = new Box(x0, bottomY, z0, x0 + 16, bottomY + height, z0 + 16);
            for (Entity e : world.getOtherEntities(null, box, e -> comp.countedEntities.contains(e.getType()))) {
                EntityType<?> t = e.getType();
                res.entityCounts.addTo(t, 1);
            }
        }
        phase = Phase.NESTS;
    }

    private void analyseNest() {
        if (nestIdx >= nestPositions.size()) {
            phase = Phase.SPAWNERS;
            return;
        }
        BlockPos pos = nestPositions.get(nestIdx++);
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BeehiveBlock)) return;
        int honey = state.contains(BeehiveBlock.HONEY_LEVEL) ? state.get(BeehiveBlock.HONEY_LEVEL) : -1;
        boolean underground = isUnderground(pos);
        String biome = world.getBiome(pos).getKey().map(k -> k.getValue().toString()).orElse("?");

        int r = comp.treeRadius;
        int logs = 0;
        int leaves = 0;
        // Liegt ein Teil des Suchwürfels in einem nicht geladenen Chunk, ist die Baumstruktur
        // unbekannt (-1) - sonst würde ein Baum im Nachbarchunk fälschlich "fehlen".
        boolean complete = world.getChunkManager().isChunkLoaded((pos.getX() - r) >> 4, (pos.getZ() - r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() + r) >> 4, (pos.getZ() - r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() - r) >> 4, (pos.getZ() + r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() + r) >> 4, (pos.getZ() + r) >> 4);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r && complete; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState s = world.getBlockState(m);
                    if (s.isIn(BlockTags.LOGS)) logs++;
                    else if (s.isIn(BlockTags.LEAVES)) leaves++;
                }
            }
        }
        if (!complete) {
            logs = -1;
            leaves = -1;
        }

        int bees = -1;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BeehiveBlockEntity hive && !hive.hasNoBees()) {
            bees = hive.getBeeCount();
        }
        res.nests.add(new ChunkResult.NestFact(pos.toImmutable(), state.isOf(Blocks.BEEHIVE), honey, underground, biome, logs, leaves, bees));
        BeeCounts.request(world, pos);
    }

    /**
     * Oberirdisch, wenn der Block die oberste Nicht-Laub-Schicht ist, darüber nur Luft/Laub/Stamm
     * bis zum Himmel kommt oder ein Nachbar fast volles Himmelslicht hat.
     */
    private boolean isUnderground(BlockPos pos) {
        if (!world.getDimension().hasSkyLight()) return true;
        int local = (pos.getZ() & 15) * 16 + (pos.getX() & 15);
        int top = topNoLeaves[local];
        if (pos.getY() >= top) return false;
        boolean clear = true;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = pos.getY() + 1; y <= top; y++) {
            BlockState s = world.getBlockState(m.set(pos.getX(), y, pos.getZ()));
            if (!(s.isAir() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isOf(Blocks.VINE)
                    || s.isOf(Blocks.SNOW) || s.isOf(Blocks.BEE_NEST) || s.isOf(Blocks.BEEHIVE) || s.isReplaceable())) {
                clear = false;
                break;
            }
        }
        if (clear) return false;
        int sky = 0;
        for (Direction d : Direction.values()) {
            sky = Math.max(sky, world.getLightLevel(LightType.SKY, pos.offset(d)));
        }
        return sky < 12;
    }

    /**
     * Ein Spawner pro Schritt: Mob (wie ihn der Client zum Rendern kennt), freier Himmel und die
     * typischen Blöcke jeder Struktur-Umgebung im Würfel um den Spawner.
     */
    private void analyseSpawner() {
        if (spawnerIdx >= spawnerPositions.size()) {
            phase = Phase.KELP;
            return;
        }
        BlockPos pos = spawnerPositions.get(spawnerIdx++);
        BlockState state = world.getBlockState(pos);
        boolean trial = state.isOf(Blocks.TRIAL_SPAWNER);
        if (!trial && !state.isOf(Blocks.SPAWNER)) return;

        String mob = "?";
        if (!trial && world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
            Entity e = spawner.getLogic().getRenderedEntity(world, pos);
            mob = e == null ? null : Registries.ENTITY_TYPE.getId(e.getType()).toString();
        }

        int local = (pos.getZ() & 15) * 16 + (pos.getX() & 15);
        boolean openSky = world.getDimension().hasSkyLight() && pos.getY() >= topNoLeaves[local];

        List<Compiled.SpawnerCtx> contexts = comp.spawnerContexts;
        int r = comp.spawnerMaxRadius;
        int[] counts = new int[contexts.size()];
        boolean complete = world.getChunkManager().isChunkLoaded((pos.getX() - r) >> 4, (pos.getZ() - r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() + r) >> 4, (pos.getZ() - r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() - r) >> 4, (pos.getZ() + r) >> 4)
                && world.getChunkManager().isChunkLoaded((pos.getX() + r) >> 4, (pos.getZ() + r) >> 4);
        if (complete) {
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockState s = world.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                        if (s.isAir()) continue;
                        Block b = s.getBlock();
                        int dist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                        for (int i = 0; i < counts.length; i++) {
                            Compiled.SpawnerCtx c = contexts.get(i);
                            if (dist <= c.radius() && c.blocks().contains(b)) counts[i]++;
                        }
                    }
                }
            }
        } else {
            // Umgebung reicht in einen nicht geladenen Chunk: unbekannt, wird beim Nachladen erneut geprüft
            counts = null;
        }
        res.spawners.add(new ChunkResult.SpawnerFact(pos.toImmutable(), trial, mob, openSky, counts));
    }

    private void measureKelp() {
        int end = Math.min(kelpHeads.size(), kelpIdx + 32);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (; kelpIdx < end; kelpIdx++) {
            long packed = kelpHeads.getLong(kelpIdx);
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            int h = 0;
            int yy = y;
            while (yy >= bottomY) {
                BlockState s = chunk.getBlockState(m.set(x, yy, z));
                if (!(s.isOf(Blocks.KELP) || s.isOf(Blocks.KELP_PLANT))) break;
                h++;
                yy--;
            }
            if (h >= 2) kelpFound.add(new ChunkResult.KelpColumn(new BlockPos(x, y, z), h));
        }
        if (kelpIdx >= kelpHeads.size()) {
            kelpFound.sort((a, b) -> Integer.compare(b.height(), a.height()));
            for (int i = 0; i < Math.min(MAX_KELP_PER_CHUNK, kelpFound.size()); i++) res.kelp.add(kelpFound.get(i));
            phase = comp.lightCheckOn ? Phase.LIGHT_PREP : Phase.DONE;
        }
    }

    // ------------------------------------------------------------------ Licht-Check

    /**
     * Sucht Sektionen mit Blocklicht unter der Oberfläche. Nur dort wird später geprüft.
     */
    private void lightPrep() {
        ChunkLightingView blockLight = world.getLightingProvider().get(LightType.BLOCK);
        int maxTop = Integer.MIN_VALUE;
        for (int t : topNoLeaves) maxTop = Math.max(maxTop, t);
        lightMinY = Integer.MAX_VALUE;
        lightMaxY = Integer.MIN_VALUE;
        for (int i = 0; i < sections.length; i++) {
            int sy = chunk.sectionIndexToCoord(i);
            int baseY = ChunkSectionPos.getBlockCoord(sy);
            if (baseY >= maxTop) continue; // komplett über der Oberfläche
            ChunkNibbleArray arr = blockLight.getLightSection(ChunkSectionPos.from(res.cx, sy, res.cz));
            if (arr == null || arr.isUninitialized()) continue;
            lightSections.add(i);
            lightMinY = Math.min(lightMinY, baseY);
            lightMaxY = Math.max(lightMaxY, baseY + 15);
        }
        res.lightChecked = true;
        if (lightSections.isEmpty()) {
            phase = Phase.DONE;
            return;
        }
        lightMinY = Math.max(bottomY, lightMinY - 15);
        lightMaxY = Math.min(bottomY + height - 1, lightMaxY + 15);
        // Ohne natürliche Lichtquelle bleibt das Feld 0 – die Ausbreitung kann entfallen.
        phase = naturalFilled ? Phase.LIGHT_X : Phase.LIGHT_JUDGE;
    }

    /*
     * Natürliches Licht-Feld: Für jede Position die höchstmögliche Helligkeit, die eine natürliche
     * Quelle dort erzeugen könnte (Leuchtkraft minus Manhattan-Abstand, ohne Rücksicht auf Wände –
     * also eine großzügige Obergrenze). Die L1-Distanztransformation ist separierbar: je ein
     * Vorwärts- und Rückwärtslauf entlang X, Z und Y.
     */

    private void lightPassX() {
        byte[] f = lightField;
        for (int wy = lightMinY; wy <= lightMaxY; wy++) {
            int yb = (wy - bottomY) * 256;
            for (int z = 0; z < 16; z++) {
                int row = yb + z * 16;
                for (int x = 1; x < 16; x++) relax(f, row + x, row + x - 1);
                for (int x = 14; x >= 0; x--) relax(f, row + x, row + x + 1);
            }
        }
        phase = Phase.LIGHT_Z;
    }

    private void lightPassZ() {
        byte[] f = lightField;
        for (int wy = lightMinY; wy <= lightMaxY; wy++) {
            int yb = (wy - bottomY) * 256;
            for (int x = 0; x < 16; x++) {
                for (int z = 1; z < 16; z++) relax(f, yb + z * 16 + x, yb + (z - 1) * 16 + x);
                for (int z = 14; z >= 0; z--) relax(f, yb + z * 16 + x, yb + (z + 1) * 16 + x);
            }
        }
        phase = Phase.LIGHT_Y;
    }

    private void lightPassY() {
        byte[] f = lightField;
        int y0 = lightMinY - bottomY;
        int y1 = lightMaxY - bottomY;
        for (int col = 0; col < 256; col++) {
            for (int y = y0 + 1; y <= y1; y++) relax(f, y * 256 + col, (y - 1) * 256 + col);
            for (int y = y1 - 1; y >= y0; y--) relax(f, y * 256 + col, (y + 1) * 256 + col);
        }
        phase = Phase.LIGHT_JUDGE;
    }

    private static void relax(byte[] f, int idx, int from) {
        int v = f[from] - 1;
        if (v > f[idx]) f[idx] = (byte) v;
    }

    /**
     * Prüft eine Sektion: Luftblöcke unter der Oberfläche mit Blocklicht L, das keine natürliche
     * Quelle erreichen kann (Feld < L). Blöcke nahe am Chunkrand werden nur gewertet, wenn L so
     * hoch ist, dass eine Quelle im Nachbarchunk es nicht erklären könnte.
     */
    private void lightJudge() {
        if (judgeIdx >= lightSections.size()) {
            phase = Phase.DONE;
            return;
        }
        int i = lightSections.getInt(judgeIdx++);
        ChunkSection sec = sections[i];
        int sy = chunk.sectionIndexToCoord(i);
        int baseY = ChunkSectionPos.getBlockCoord(sy);
        ChunkNibbleArray arr = world.getLightingProvider().get(LightType.BLOCK).getLightSection(ChunkSectionPos.from(res.cx, sy, res.cz));
        if (arr == null || arr.isUninitialized()) return;
        int found = 0;
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            int yb = (wy - bottomY) * 256;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int l = arr.get(x, y, z);
                    if (l == 0) continue;
                    int dOut = Math.min(Math.min(x, 15 - x), Math.min(z, 15 - z)) + 1;
                    if (l + dOut < 16) continue;
                    if (wy >= topNoLeaves[z * 16 + x]) continue;
                    if (lightField[yb + z * 16 + x] >= l) continue;
                    if (sec != null && !sec.getBlockState(x, y, z).isAir()) continue;
                    found++;
                }
            }
        }
        if (found > 0) {
            res.litArtificialAir += found;
            res.litSectionMask |= 1L << Math.min(i, 63);
        }
    }

    private void finish() {
        if (comp.anomalyOn) {
            res.structureStrength = new double[StructureSignatures.ALL.size()];
            for (int i = 0; i < res.structureStrength.length; i++) {
                res.structureStrength[i] = StructureSignatures.ALL.get(i).strength(res.counts);
            }
        }
    }
}
