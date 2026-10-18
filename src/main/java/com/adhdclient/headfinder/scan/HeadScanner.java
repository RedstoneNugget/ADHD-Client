package com.adhdclient.headfinder.scan;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.detect.BlockESP;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sucht Blöcke, die zu einem der konfigurierten Muster passen, in den Daten, die der Client
 * ohnehin hat:
 *
 *  - Beim Laden eines Chunks wird dieser einmal komplett nach Ziel-Blöcken durchsucht.
 *  - Alle {@code rescanIntervalTicks} werden alle geladenen Chunks erneut geprüft (fängt
 *    nachträglich gesetzte/abgebaute Blöcke ab) – pro Sektion wird zuerst die Palette geprüft,
 *    sodass Sektionen ohne Ziel-Block gar nicht erst Block für Block durchlaufen werden.
 *  - Im gleichen Intervall: Items am Boden / in Item-Frames sowie die "verschwunden"-Prüfung.
 *
 * Ergebnisse landen im {@link HeadStore}; gerendert wird nur aus dem Cache.
 */
public final class HeadScanner {

    /** Höchstens so viele frisch geladene Chunks pro Tick prüfen (Lastspitzen beim Teleport vermeiden). */
    private static final int CHUNKS_PER_TICK = 32;

    private final BlockESP matcher;
    private final HeadStore store;

    private ClientWorld trackedWorld;
    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    private int tickCounter;
    /** Statistik für /headfinder status. */
    private int lastMatchCount;
    /** Dauer des letzten kompletten Intervall-Scans (Mikrosekunden). */
    private long lastIntervalMicros;

    public HeadScanner(BlockESP matcher, HeadStore store) {
        this.matcher = matcher;
        this.store = store;
    }

    // ---------------------------------------------------------------- Chunk-Ereignisse

    public void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        syncWorld(world);
        long key = chunk.getPos().toLong();
        loadedChunks.add(key);
        pending.enqueue(key);
    }

    public void onChunkUnload(ClientWorld world, WorldChunk chunk) {
        if (world == trackedWorld) loadedChunks.remove(chunk.getPos().toLong());
    }

    /** Neue Welt (Server/Dimension gewechselt) → Chunk-Liste zurücksetzen. */
    private void syncWorld(ClientWorld world) {
        if (world != trackedWorld) {
            trackedWorld = world;
            loadedChunks.clear();
            pending.clear();
            tickCounter = 0;
        }
    }

    /** Erzwingt einen kompletten Durchlauf im nächsten Tick. */
    public void requestRescan() {
        tickCounter = Integer.MAX_VALUE - 1;
    }

    public int loadedChunkCount() {
        return loadedChunks.size();
    }

    public long lastIntervalMicros() {
        return lastIntervalMicros;
    }

    public int lastMatchCount() {
        return lastMatchCount;
    }

    // ---------------------------------------------------------------- Tick

    public void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || !store.isOpen()) {
            if (world == null) syncWorld(null);
            return;
        }
        syncWorld(world);
        ModConfig cfg = HeadFinderClient.config();
        String dim = dimension(world);

        if (!matcher.hasCriteria()) return;

        // 1) frisch geladene Chunks
        int budget = CHUNKS_PER_TICK;
        while (!pending.isEmpty() && budget-- > 0) {
            long key = pending.dequeueLong();
            if (!loadedChunks.contains(key)) continue;
            WorldChunk chunk = loadedChunk(world, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk != null) scanChunk(chunk, dim);
        }

        // 2) Intervall: alles erneut prüfen
        if (++tickCounter >= cfg.rescanIntervalTicks) {
            tickCounter = 0;
            long t0 = System.nanoTime();
            int matches = 0;
            LongIterator it = loadedChunks.iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                WorldChunk chunk = loadedChunk(world, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
                if (chunk != null) matches += scanChunk(chunk, dim);
                else it.remove(); // ohne Unload-Ereignis entladen (z. B. nach Teleport)
            }
            lastMatchCount = matches;
            scanEntities(world, dim, cfg);
            if (cfg.foundOnDisappear) checkDisappeared(world, dim);
            lastIntervalMicros = (System.nanoTime() - t0) / 1000;
        }
    }

    /** Durchsucht einen Chunk sektionsweise nach Ziel-Blöcken. Gibt die Zahl der Treffer zurück. */
    private int scanChunk(WorldChunk chunk, String dim) {
        Set<Block> targets = matcher.targets();
        if (targets.isEmpty()) return 0;

        int found = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.getBlockStateContainer().hasAny(state -> targets.contains(state.getBlock()))) continue;

            int baseY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            for (int y = 0; y < 16; y++) {
                int wy = baseY + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        BlockESP.Match m = matcher.match(s);
                        if (m == null) continue;
                        found++;
                        BlockPos pos = new BlockPos(baseX + x, wy, baseZ + z);
                        if (store.discover(dim, pos, HeadEntry.Kind.BLOCK, m.profileName())) announceNew(pos);
                    }
                }
            }
        }
        return found;
    }

    /** Ziel-Blöcke als Item am Boden (nur live) und in Item-Frames (gespeichert). */
    private void scanEntities(ClientWorld world, String dim, ModConfig cfg) {
        if (!cfg.detectItems) {
            store.replaceItems(List.of());
            return;
        }
        List<HeadEntry> items = new ArrayList<>();
        Map<BlockPos, ItemFrameEntity> frames = new HashMap<>();
        for (Entity e : world.getEntities()) {
            if (e instanceof ItemEntity item) {
                BlockESP.Match m = matcher.match(item.getStack());
                if (m != null) {
                    HeadEntry h = new HeadEntry(dim, item.getBlockPos(), HeadEntry.Kind.ITEM, m.profileName());
                    h.entityId = item.getId();
                    h.livePos = item.getEntityPos();
                    items.add(h);
                }
            } else if (e instanceof ItemFrameEntity frame) {
                frames.put(frame.getBlockPos(), frame);
                BlockESP.Match m = matcher.match(frame.getHeldItemStack());
                if (m != null && store.discover(dim, frame.getBlockPos(), HeadEntry.Kind.FRAME, m.profileName())) {
                    announceNew(frame.getBlockPos());
                }
            }
        }
        store.replaceItems(items);

        // Rahmen, der noch existiert, aber kein passendes Item mehr hält → gefunden
        if (cfg.foundOnDisappear) {
            for (HeadEntry h : store.inDimension(dim)) {
                if (h.kind != HeadEntry.Kind.FRAME || h.found) continue;
                ItemFrameEntity frame = frames.get(h.pos());
                if (frame != null && matcher.match(frame.getHeldItemStack()) == null) {
                    store.setFound(h, true, "aus Rahmen entfernt");
                }
            }
        }
    }

    /** Ziel-Block in einem geladenen Chunk nicht mehr vorhanden (abgebaut/ersetzt) → gilt als eingesammelt. */
    private void checkDisappeared(ClientWorld world, String dim) {
        for (HeadEntry h : store.inDimension(dim)) {
            if (h.kind != HeadEntry.Kind.BLOCK || h.found) continue;
            if (!loadedChunks.contains(ChunkPos.toLong(h.x >> 4, h.z >> 4))) continue;
            // Nur echte Chunk-Daten zählen: world.isChunkLoaded() kann für entladene Chunks noch true sein,
            // world.getBlockState() liefert dann void_air aus einem leeren Platzhalter-Chunk.
            WorldChunk chunk = loadedChunk(world, h.x >> 4, h.z >> 4);
            if (chunk == null) continue;
            BlockPos pos = h.pos();
            BlockState state = chunk.getBlockState(pos);
            if (matcher.match(state) == null) {
                HeadFinderClient.LOGGER.info("Block bei {} ist verschwunden (jetzt {}) – gilt als gefunden", pos.toShortString(), state.getBlock());
                store.setFound(h, true, "verschwunden");
            }
        }
    }

    private void announceNew(BlockPos pos) {
        if (!HeadFinderClient.config().notifyNewHeads) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.literal("Neuer Fund: " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .formatted(Formatting.GOLD), false);
        }
    }

    /**
     * Der echte, vollständig geladene Chunk – oder null. Für nicht (mehr) geladene Positionen gibt der Client
     * sonst einen leeren Platzhalter zurück, dessen Blöcke alle void_air sind.
     */
    public static WorldChunk loadedChunk(ClientWorld world, int cx, int cz) {
        WorldChunk c = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (c == null || c instanceof EmptyChunk) return null;
        ChunkPos p = c.getPos();
        return p.x == cx && p.z == cz ? c : null;
    }

    public static String dimension(ClientWorld world) {
        return world.getRegistryKey().getValue().toString();
    }
}
