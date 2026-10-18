package com.adhdclient.basefinder.scan;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.basefinder.config.ModConfig;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herz der Mod: hält den Cache (Chunk -> Rohdaten), entscheidet, welcher Chunk als Nächstes
 * gescannt wird, und verteilt die Arbeit mit einem Zeitbudget auf die Ticks.
 *
 *  - CHUNK_LOAD: Chunk wird nach kurzer Verzögerung (Lichtdaten) gescannt.
 *  - CHUNK_UNLOAD: Cache-Eintrag fliegt raus.
 *  - Block-Änderung (Mixin auf ClientWorld): Chunk wird "dirty" und gebündelt nach ~1,5 s neu gescannt.
 *  - Hintergrund-Durchlauf: alle X Sekunden werden alle gecachten Chunks erneut geprüft.
 *  - Pro Tick höchstens scanBudgetMs Millisekunden Arbeit, der Rest folgt in späteren Ticks.
 *
 * Alles läuft auf dem Client-Thread; der Cache ist trotzdem eine ConcurrentHashMap, weil die
 * Bienen-Abfrage im Einzelspieler Ergebnisse vom Server-Thread zurückmeldet.
 */
public final class ScanManager {

    public static final ScanManager INSTANCE = new ScanManager();

    private static final long EVAL_INTERVAL_MS = 250;
    private static final long EVAL_REFRESH_MS = 1000;

    private final Map<Long, ChunkResult> cache = new ConcurrentHashMap<>();
    /** Chunk -> Zeitpunkt (ms), ab dem er gescannt werden darf. */
    private final Long2LongOpenHashMap pending = new Long2LongOpenHashMap();
    private final LongArrayList background = new LongArrayList();
    private int backgroundIdx;
    private ChunkScanJob job;

    private ClientWorld world;
    private Compiled compiled;
    private boolean configDirty;

    private volatile Evaluator.Results results = Evaluator.Results.EMPTY;
    private boolean resultsDirty;
    private long lastEval;
    private long nextRadiusCheck;
    private long nextBackground;

    private final LongOpenHashSet fullScanRemaining = new LongOpenHashSet();
    private boolean fullScanActive;

    public final Notifier notifier = new Notifier();

    // Statistik für das Menü
    private long scannedChunks;
    private double lastTickMs;
    private double peakTickMs;

    private ScanManager() {
    }

    private static ModConfig cfg() {
        return BaseFinderClient.config();
    }

    // ------------------------------------------------------------------ öffentliche Abfragen

    public Evaluator.Results results() {
        return results;
    }

    public int cachedCount() {
        return cache.size();
    }

    public int pendingCount() {
        return pending.size() + (job != null ? 1 : 0);
    }

    public long scannedChunks() {
        return scannedChunks;
    }

    public double lastTickMs() {
        return lastTickMs;
    }

    /** Höchste gemessene Tick-Dauer seit dem letzten Abruf (für Tests und Leistungsprüfung). */
    public double takePeakTickMs() {
        double p = peakTickMs;
        peakTickMs = 0;
        return p;
    }

    public Map<Long, ChunkResult> cache() {
        return cache;
    }

    /** Anteil der geladenen Chunks im Radius, die bereits im Cache sind (0..1). */
    public double coverage() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (world == null || mc.player == null) return 0;
        int r = radius();
        ChunkPos pc = mc.player.getChunkPos();
        int loaded = 0;
        int cached = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!world.getChunkManager().isChunkLoaded(pc.x + dx, pc.z + dz)) continue;
                loaded++;
                if (cache.containsKey(ChunkPos.toLong(pc.x + dx, pc.z + dz))) cached++;
            }
        }
        return loaded == 0 ? 0 : cached / (double) loaded;
    }

    public boolean isFullScanActive() {
        return fullScanActive;
    }

    public boolean isIdle() {
        return job == null && pending.isEmpty();
    }

    // ------------------------------------------------------------------ Ereignisse

    public void onChunkLoad(ClientWorld w, WorldChunk chunk) {
        if (w != world || world == null) return;
        ChunkPos p = chunk.getPos();
        if (!inRadius(p.x, p.z, radius())) return;
        long due = System.currentTimeMillis() + cfg().general.loadDelayMs;
        pending.put(p.toLong(), due);
        // Nachbarn mit Nestern/Spawnern, deren Umgebung wegen dieses Chunks unbekannt war, neu prüfen
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long k = ChunkPos.toLong(p.x + dx, p.z + dz);
                ChunkResult r = cache.get(k);
                if (r != null && (r.nests.stream().anyMatch(n -> n.logs < 0)
                        || r.spawners.stream().anyMatch(s -> s.contextCounts() == null))) pending.putIfAbsent(k, due);
            }
        }
    }

    public void onChunkUnload(ClientWorld w, WorldChunk chunk) {
        if (w != world || world == null) return;
        long key = chunk.getPos().toLong();
        if (cache.remove(key) != null) resultsDirty = true;
        pending.remove(key);
        background.rem(key);
        if (job != null && job.key() == key) job.abort();
        if (fullScanRemaining.remove(key)) checkFullScanDone();
    }

    /** Vom Mixin: ein Block im Client-World hat sich geändert. */
    public void onBlockChanged(ClientWorld w, BlockPos pos, BlockState oldState, BlockState newState) {
        if (w != world || world == null) return;
        // Nur Änderungen, die ein Ergebnis beeinflussen können (gesuchte/leuchtende Blöcke).
        // Gras, das sich ausbreitet, fließendes Wasser usw. lösen keinen Nachscan aus –
        // dafür gibt es den langsamen Hintergrund-Durchlauf.
        if (compiled != null && oldState != null && newState != null
                && !compiled.isRelevant(oldState) && !compiled.isRelevant(newState)) return;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.toLong(cx, cz);
        if (pending.containsKey(key)) return; // schon vorgemerkt -> bündeln
        if (!cache.containsKey(key) && !inRadius(cx, cz, radius())) return;
        pending.put(key, System.currentTimeMillis() + cfg().general.dirtyDelayMs);
    }

    // ---- Fundliste: ausgeblendete Funde und Fokus gelten nur für die aktuelle Welt
    private final Set<String> hidden = new HashSet<>();
    private volatile String focusKey;

    public void hide(String key) {
        hidden.add(key);
        if (key.equals(focusKey)) focusKey = null;
        resultsDirty = true;
        lastEval = 0;
    }

    public void unhideAll() {
        hidden.clear();
        resultsDirty = true;
        lastEval = 0;
    }

    public int hiddenCount() {
        return hidden.size();
    }

    /** Nur diesen einen Fund zeigen (null = alle). */
    public void setFocus(String key) {
        focusKey = key;
    }

    public String focusKey() {
        return focusKey;
    }

    public void configChanged() {
        configDirty = true;
    }

    /** Taste J: alle geladenen Chunks im Radius sofort neu scannen und danach zusammenfassen. */
    public int forceFullRescan() {
        if (world == null) return 0;
        int n = rescanAll();
        fullScanRemaining.clear();
        fullScanRemaining.addAll(pending.keySet());
        fullScanActive = true;
        if (n == 0) checkFullScanDone();
        return n;
    }

    // ------------------------------------------------------------------ Tick

    public void tick(MinecraftClient mc) {
        long t0 = System.nanoTime();
        ClientWorld w = mc.world;
        if (w != world) {
            reset();
            world = w;
            if (w != null) compiled = new Compiled(cfg());
        }
        if (world == null || mc.player == null) return;
        ModConfig cfg = cfg();
        long now = System.currentTimeMillis();

        if (configDirty) {
            configDirty = false;
            String oldSig = compiled == null ? "" : compiled.scanSignature;
            compiled = new Compiled(cfg);
            if (!compiled.scanSignature.equals(oldSig)) rescanAll();
            resultsDirty = true;
        }

        int radius = radius();
        if (now >= nextRadiusCheck) {
            nextRadiusCheck = now + 1000;
            maintainRadius(mc, radius, now);
        }

        boolean scanning = (cfg.general.constantScanning || fullScanActive) && compiled.anyModeOn();
        if (scanning) {
            if (cfg.general.constantScanning && cfg.general.backgroundIntervalSec > 0 && now >= nextBackground) {
                nextBackground = now + cfg.general.backgroundIntervalSec * 1000L;
                if (backgroundIdx >= background.size()) {
                    background.clear();
                    backgroundIdx = 0;
                    for (Long k : cache.keySet()) background.add(k.longValue());
                }
            }
            runBudget(mc, cfg, now);
        }

        if (BeeCounts.consumeChanged()) resultsDirty = true;
        if ((resultsDirty && now - lastEval >= EVAL_INTERVAL_MS) || now - lastEval >= EVAL_REFRESH_MS) {
            evaluate(mc, now);
        }
        notifier.tick(cfg, now);
        lastTickMs = (System.nanoTime() - t0) / 1e6;
        peakTickMs = Math.max(peakTickMs, lastTickMs);
    }

    private void runBudget(MinecraftClient mc, ModConfig cfg, long now) {
        long deadline = System.nanoTime() + (long) (cfg.general.scanBudgetMs * 1_000_000L);
        do {
            if (job == null) {
                job = nextJob(mc, now);
                if (job == null) break;
            }
            boolean done;
            try {
                done = job.step();
            } catch (RuntimeException e) {
                // Ein kaputter Chunk darf das Spiel nicht stören: überspringen und protokollieren.
                BaseFinderClient.LOGGER.warn("Scan von Chunk {} fehlgeschlagen", new ChunkPos(job.key()), e);
                job.abort();
                done = true;
            }
            if (done) {
                ChunkScanJob j = job;
                job = null;
                if (!j.aborted()) {
                    cache.put(j.key(), j.result());
                    scannedChunks++;
                    resultsDirty = true;
                }
                if (fullScanRemaining.remove(j.key())) checkFullScanDone();
            }
        } while (System.nanoTime() < deadline);
    }

    private ChunkScanJob nextJob(MinecraftClient mc, long now) {
        int radius = radius();
        ChunkPos pc = mc.player.getChunkPos();
        // 1) fällige Dirty-/Lade-Einträge, nächster Chunk zuerst
        while (true) {
            long bestKey = Long.MIN_VALUE;
            long bestDist = Long.MAX_VALUE;
            for (Long2LongOpenHashMap.Entry e : pending.long2LongEntrySet()) {
                if (e.getLongValue() > now) continue;
                long k = e.getLongKey();
                long dx = ChunkPos.getPackedX(k) - pc.x;
                long dz = ChunkPos.getPackedZ(k) - pc.z;
                long d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    bestKey = k;
                }
            }
            if (bestKey == Long.MIN_VALUE) break;
            pending.remove(bestKey);
            ChunkScanJob j = createJob(bestKey, radius, now);
            if (j != null) return j;
            if (fullScanRemaining.remove(bestKey)) checkFullScanDone();
        }
        // 2) Hintergrund-Durchlauf in kleinen Portionen
        while (backgroundIdx < background.size()) {
            long k = background.getLong(backgroundIdx++);
            if (pending.containsKey(k) || !cache.containsKey(k)) continue;
            ChunkScanJob j = createJob(k, radius, now);
            if (j != null) return j;
        }
        return null;
    }

    private ChunkScanJob createJob(long key, int radius, long now) {
        int cx = ChunkPos.getPackedX(key);
        int cz = ChunkPos.getPackedZ(key);
        if (!inRadius(cx, cz, radius)) return null;
        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null || chunk.isEmpty()) return null;
        return new ChunkScanJob(world, chunk, compiled, now);
    }

    /** Einträge außerhalb des Radius verwerfen, neu in den Radius gekommene Chunks einplanen. */
    private void maintainRadius(MinecraftClient mc, int radius, long now) {
        ChunkPos pc = mc.player.getChunkPos();
        boolean removed = cache.keySet().removeIf(k -> {
            int dx = Math.abs(ChunkPos.getPackedX(k) - pc.x);
            int dz = Math.abs(ChunkPos.getPackedZ(k) - pc.z);
            return Math.max(dx, dz) > radius + 1;
        });
        if (removed) resultsDirty = true;
        pending.keySet().removeIf(k -> !inRadius(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k), radius + 1));
        if (!cfg().general.constantScanning) return;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = pc.x + dx;
                int z = pc.z + dz;
                long k = ChunkPos.toLong(x, z);
                if (cache.containsKey(k) || pending.containsKey(k) || (job != null && job.key() == k)) continue;
                if (world.getChunkManager().isChunkLoaded(x, z)) pending.put(k, now);
            }
        }
    }

    private int rescanAll() {
        if (job != null) job.abort();
        job = null;
        pending.clear();
        background.clear();
        backgroundIdx = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (world == null || mc.player == null) return 0;
        int radius = radius();
        ChunkPos pc = mc.player.getChunkPos();
        long now = System.currentTimeMillis();
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (world.getChunkManager().isChunkLoaded(pc.x + dx, pc.z + dz)) {
                    pending.put(ChunkPos.toLong(pc.x + dx, pc.z + dz), now);
                    n++;
                }
            }
        }
        return n;
    }

    private void checkFullScanDone() {
        if (!fullScanActive || !fullScanRemaining.isEmpty()) return;
        fullScanActive = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        evaluate(mc, System.currentTimeMillis());
        notifier.showNow(Text.literal(Evaluator.summary(results, cfg())).formatted(Formatting.AQUA));
    }

    private void evaluate(MinecraftClient mc, long now) {
        lastEval = now;
        resultsDirty = false;
        if (world == null || mc.player == null || compiled == null) {
            results = Evaluator.Results.EMPTY;
            return;
        }
        Vec3d eye = mc.player.getEyePos();
        results = Evaluator.evaluate(cache, cfg(), compiled, world.getRegistryKey(), eye, hidden);
        notifier.update(results, cache.keySet(), cfg(), now);
    }

    private void reset() {
        hidden.clear();
        focusKey = null;
        cache.clear();
        pending.clear();
        background.clear();
        backgroundIdx = 0;
        if (job != null) job.abort();
        job = null;
        results = Evaluator.Results.EMPTY;
        notifier.reset();
        BeeCounts.clear();
        fullScanRemaining.clear();
        fullScanActive = false;
        nextBackground = System.currentTimeMillis() + cfg().general.backgroundIntervalSec * 1000L;
        compiled = null;
    }

    public int radius() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int view = mc.options == null ? 32 : mc.options.getClampedViewDistance();
        return Math.max(1, Math.min(cfg().general.scanRadius, view));
    }

    private boolean inRadius(int cx, int cz, int radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        ChunkPos pc = mc.player.getChunkPos();
        return Math.max(Math.abs(cx - pc.x), Math.abs(cz - pc.z)) <= radius;
    }
}
