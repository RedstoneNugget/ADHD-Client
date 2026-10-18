package com.adhdclient.headfinder.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Ein entdeckter Kopf. Identifiziert über Dimension + Blockposition (nicht über die Profil-UUID,
 * die bei jedem Kopf anders ist).
 *
 * Die nicht-transienten Felder werden pro Server in config/head_finder/found/&lt;server&gt;.json gespeichert.
 */
public final class HeadEntry {

    public enum Kind {
        /** Kopf als Block (player_head / player_wall_head). */
        BLOCK("Block"),
        /** Kopf in einem Item-Frame. */
        FRAME("Rahmen"),
        /** Kopf als Item auf dem Boden – nur solange sichtbar, wird nicht gespeichert. */
        ITEM("Item");

        public final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    public String dim;
    public int x;
    public int y;
    public int z;
    public Kind kind = Kind.BLOCK;
    /** Profilname zum Zeitpunkt der Entdeckung (nur Info). */
    public String name = "";
    public boolean found;
    /** Zeitpunkt der Entdeckung / des Findens (ms seit 1970). */
    public long firstSeen;
    public long foundAt;
    /** Wie der Kopf als gefunden markiert wurde (Rechtsklick, verschwunden, manuell …). */
    public String foundReason = "";

    /** Nur für ITEM: aktuelle Entity-ID und genaue Position (nicht gespeichert). */
    public transient int entityId = -1;
    public transient Vec3d livePos;

    public HeadEntry() {
    }

    public HeadEntry(String dim, BlockPos pos, Kind kind, String name) {
        this.dim = dim;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.kind = kind;
        this.name = name == null ? "" : name;
        this.firstSeen = System.currentTimeMillis();
    }

    public static String key(String dim, BlockPos pos, Kind kind) {
        return key(dim, pos.getX(), pos.getY(), pos.getZ(), kind);
    }

    public static String key(String dim, int x, int y, int z, Kind kind) {
        String prefix = kind == Kind.FRAME ? "F|" : "";
        return prefix + dim + "|" + x + "|" + y + "|" + z;
    }

    public String key() {
        if (kind == Kind.ITEM) return "I|" + entityId;
        return key(dim, x, y, z, kind);
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    /** Mittelpunkt für Entfernung, Tracer und Pfeil. */
    public Vec3d center() {
        if (kind == Kind.ITEM && livePos != null) return livePos.add(0, 0.25, 0);
        if (kind == Kind.FRAME) return new Vec3d(x + 0.5, y + 0.5, z + 0.5);
        return new Vec3d(x + 0.5, y + 0.25, z + 0.5);
    }

    public String coords() {
        return x + " " + y + " " + z;
    }

    /** Kurzname der Dimension, z. B. "overworld" statt "minecraft:overworld". */
    public String dimShort() {
        if (dim == null) return "?";
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }
}
