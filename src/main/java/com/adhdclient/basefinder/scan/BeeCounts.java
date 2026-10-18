package com.adhdclient.basefinder.scan;

import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bienenanzahl in Nestern.
 *
 * Vanilla-Server schicken die Bienen in einem Nest nicht an den Client – auf fremden Servern ist
 * die Anzahl daher meist unbekannt ("?"). Nur im Einzelspieler (integrierter Server im selben
 * Prozess) wird sie direkt gelesen: Die Abfrage läuft als Aufgabe auf dem Server-Thread, das
 * Ergebnis landet in einer thread-sicheren Map. Es werden keine Pakete gesendet.
 */
public final class BeeCounts {

    private static final Map<Long, Integer> COUNTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean CHANGED = new AtomicBoolean();

    private BeeCounts() {
    }

    public static void request(ClientWorld world, BlockPos pos) {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (server == null) return;
        RegistryKey<World> key = world.getRegistryKey();
        long packed = pos.asLong();
        BlockPos p = pos.toImmutable();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(key);
            if (sw == null || !sw.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return;
            BlockEntity be = sw.getBlockEntity(p);
            int n = be instanceof BeehiveBlockEntity hive ? hive.getBeeCount() : -1;
            Integer old = COUNTS.put(packed, n);
            if (old == null || old != n) CHANGED.set(true);
        });
    }

    /** Bekannte Anzahl oder -1. */
    public static int get(BlockPos pos) {
        Integer n = COUNTS.get(pos.asLong());
        return n == null ? -1 : n;
    }

    public static boolean consumeChanged() {
        return CHANGED.getAndSet(false);
    }

    public static void clear() {
        COUNTS.clear();
    }
}
