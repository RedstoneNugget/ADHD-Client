package com.adhdclient.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hält für jeden geladenen Chunk die Positionen der ausgewählten Blöcke. Chunks werden beim Laden,
 * nach Block-Änderungen und nach einer Änderung der Auswahl neu durchsucht – verteilt auf mehrere Ticks.
 * Pro Sektion wird zuerst die Palette geprüft, Sektionen ohne ausgewählten Block werden übersprungen.
 */
public final class BlockESPScanner {

    private static final long BUDGET_NANOS = 2_000_000L;

    private final Long2ObjectOpenHashMap<LongArrayList> found = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet loaded = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private ClientWorld world;
    private Set<Block> targets = Set.of();

    public void setSelection(List<String> ids) {
        Set<Block> out = new HashSet<>();
        for (String id : ids) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null && Registries.BLOCK.containsId(ident)) out.add(Registries.BLOCK.get(ident));
        }
        targets = out;
        found.clear();
        for (long key : loaded) enqueue(key);
    }

    public Long2ObjectOpenHashMap<LongArrayList> found() {
        return found;
    }

    public int count() {
        int n = 0;
        for (LongArrayList l : found.values()) n += l.size();
        return n;
    }

    public void onChunkLoad(ClientWorld w, WorldChunk chunk) {
        sync(w);
        long key = chunk.getPos().toLong();
        loaded.add(key);
        enqueue(key);
    }

    public void onChunkUnload(ClientWorld w, WorldChunk chunk) {
        if (w != world) return;
        long key = chunk.getPos().toLong();
        loaded.remove(key);
        found.remove(key);
    }

    public void onBlockChanged(BlockPos pos) {
        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (loaded.contains(key)) enqueue(key);
    }

    private void enqueue(long key) {
        if (queued.add(key)) queue.enqueue(key);
    }

    private void sync(ClientWorld w) {
        if (w == world) return;
        world = w;
        found.clear();
        loaded.clear();
        queue.clear();
        queued.clear();
    }

    public void tick(MinecraftClient mc) {
        if (mc.world == null) {
            sync(null);
            return;
        }
        sync(mc.world);
        if (targets.isEmpty()) {
            queue.clear();
            queued.clear();
            return;
        }
        long start = System.nanoTime();
        while (!queue.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
            long key = queue.dequeueLong();
            queued.remove(key);
            if (!loaded.contains(key)) continue;
            WorldChunk chunk = loadedChunk(world, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) {
                loaded.remove(key);
                found.remove(key);
                continue;
            }
            LongArrayList hits = scan(chunk);
            if (hits.isEmpty()) found.remove(key);
            else found.put(key, hits);
        }
    }

    private LongArrayList scan(WorldChunk chunk) {
        LongArrayList hits = new LongArrayList();
        Set<Block> t = targets;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.getBlockStateContainer().hasAny(s -> t.contains(s.getBlock()))) continue;
            int baseY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (t.contains(s.getBlock())) hits.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        return hits;
    }

    /** Der echte, geladene Chunk – oder null (der Client liefert sonst leere Platzhalter-Chunks). */
    private static WorldChunk loadedChunk(ClientWorld world, int cx, int cz) {
        WorldChunk c = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (c == null || c instanceof EmptyChunk) return null;
        ChunkPos p = c.getPos();
        return p.x == cx && p.z == cz ? c : null;
    }
}
