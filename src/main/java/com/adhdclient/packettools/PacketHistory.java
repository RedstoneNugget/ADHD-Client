package com.adhdclient.packettools;

import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Verlauf aller Pakete der eigenen Verbindung (gesendet und empfangen) für das große Paket-Log.
 * Ringpuffer mit den letzten {@link #MAX} Einträgen; läuft nur, solange Packet Tools an ist.
 */
public final class PacketHistory {

    public static final int MAX = 5000;
    private static final int DETAIL_MAX = 300;

    /** Ein Eintrag: Zeit (ms), Richtung, Paketname, kurze Details (nur bei Record-Paketen). */
    public record Entry(long time, boolean outgoing, String name, String detail) {
    }

    private static final Entry[] RING = new Entry[MAX];
    private static int next;
    private static int size;
    private static long total;
    /** Pause: neue Pakete werden nicht aufgenommen. */
    public static volatile boolean paused;

    private PacketHistory() {
    }

    public static void add(Packet<?> packet, boolean outgoing) {
        if (!PacketToolsClient.enabled || paused) return;
        String name;
        try {
            name = packet.getPacketType().id().getPath();
        } catch (RuntimeException e) {
            name = packet.getClass().getSimpleName();
        }
        String detail = "";
        if (packet.getClass().isRecord()) {
            try {
                detail = packet.toString();
                int bracket = detail.indexOf('[');
                if (bracket >= 0) detail = detail.substring(bracket);
                if (detail.equals("[]")) detail = "";
                if (detail.length() > DETAIL_MAX) detail = detail.substring(0, DETAIL_MAX) + " …";
            } catch (RuntimeException ignored) {
                detail = "";
            }
        }
        Entry e = new Entry(System.currentTimeMillis(), outgoing, name, detail);
        synchronized (RING) {
            RING[next] = e;
            next = (next + 1) % MAX;
            if (size < MAX) size++;
            total++;
        }
    }

    /** Alle gespeicherten Einträge, älteste zuerst. */
    public static List<Entry> snapshot() {
        synchronized (RING) {
            List<Entry> out = new ArrayList<>(size);
            int start = (next - size + MAX) % MAX;
            for (int i = 0; i < size; i++) out.add(RING[(start + i) % MAX]);
            return out;
        }
    }

    public static long total() {
        synchronized (RING) {
            return total;
        }
    }

    public static void clear() {
        synchronized (RING) {
            java.util.Arrays.fill(RING, null);
            next = 0;
            size = 0;
            total = 0;
        }
    }
}
