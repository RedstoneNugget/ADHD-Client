package com.adhdclient.inspect.snbt;

import com.adhdclient.inspect.data.Coord;
import com.adhdclient.inspect.data.Role;
import net.minecraft.nbt.AbstractNbtList;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtPrimitive;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Zerlegt ein NBT-Element in Anzeigezeilen mit Syntax-Rollen, einklappbaren
 * Abschnitten und erkannten Koordinaten. Die Zeilen werden einmal gebaut;
 * Ein-/Ausklappen und Suchen berechnen nur noch Index-Listen. Gerendert
 * werden später nur die gerade sichtbaren Zeilen.
 */
public final class SnbtTree {

    public record Seg(String text, Role role) {
    }

    public static final class Line {
        public final int depth;
        public final int parent;
        public final List<Seg> segs = new ArrayList<>(4);
        /** Bei öffnenden Zeilen: Index der schließenden Zeile, sonst -1. */
        public int end = -1;
        public int children;
        public String closer = "";
        public boolean comma;
        /** Schließende Klammer-Zeile ("}" bzw. "]"). */
        public boolean closing;
        @Nullable
        public Coord coord;
        private String lower;

        Line(int depth, int parent) {
            this.depth = depth;
            this.parent = parent;
        }

        public boolean isOpener() {
            return end >= 0;
        }

        public String plain() {
            StringBuilder sb = new StringBuilder();
            for (Seg s : segs) {
                sb.append(s.text());
            }
            return sb.toString();
        }

        public String lower() {
            if (lower == null) {
                lower = plain().toLowerCase(Locale.ROOT);
            }
            return lower;
        }
    }

    private static final int INLINE_ARRAY = 16;
    private static final int INLINE_CHARS = 72;
    private static final int MAX_STRING = 300;
    private static final int AUTO_COLLAPSE_SPAN = 80;

    public final List<Line> lines = new ArrayList<>();
    public final BitSet collapsed = new BitSet();
    public boolean truncated;
    private final int maxLines;

    public SnbtTree(NbtElement root, int maxLines) {
        this.maxLines = Math.max(100, maxLines);
        emit(null, root, 0, -1, false, null);
        if (truncated) {
            Line note = new Line(0, -1);
            note.segs.add(new Seg(com.adhdclient.gui.L.t("… gekürzt: mehr als ", "… cut off: more than ") + this.maxLines
                    + com.adhdclient.gui.L.t(" Zeilen (vollständig über \"SNBT kopieren\")", " lines (complete via \"Copy SNBT\")"), Role.WARN));
            lines.add(note);
        }
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (l.isOpener() && l.depth >= 1 && (l.end - i > AUTO_COLLAPSE_SPAN || l.closer.equals("]") && l.segs.stream().anyMatch(s -> s.text().endsWith(";")))) {
                collapsed.set(i);
            }
        }
    }

    // ------------------------------------------------------------ Sichtbarkeit

    /** Liefert die Indizes der Zeilen, die bei der aktuellen Klapp-Einstellung sichtbar sind. */
    public void collectVisible(IntConsumer out) {
        int i = 0;
        int n = lines.size();
        while (i < n) {
            out.accept(i);
            Line l = lines.get(i);
            i = l.isOpener() && collapsed.get(i) ? l.end + 1 : i + 1;
        }
    }

    /** Treffer der Suche plus deren Eltern-Zeilen (für den Kontext), in Reihenfolge. */
    public int collectMatches(String lowerQuery, IntConsumer out) {
        BitSet show = new BitSet(lines.size());
        int hits = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (l.closing) {
                continue;
            }
            if (l.lower().contains(lowerQuery)) {
                hits++;
                int p = i;
                while (p >= 0 && !show.get(p)) {
                    show.set(p);
                    p = lines.get(p).parent;
                }
            }
        }
        for (int i = show.nextSetBit(0); i >= 0; i = show.nextSetBit(i + 1)) {
            out.accept(i);
        }
        return hits;
    }

    public void toggle(int index) {
        if (index >= 0 && index < lines.size() && lines.get(index).isOpener()) {
            collapsed.flip(index);
        }
    }

    /** Alles ein- oder ausklappen (die Wurzel bleibt immer offen). */
    public void setAll(boolean collapse) {
        collapsed.clear();
        if (collapse) {
            for (int i = 0; i < lines.size(); i++) {
                Line l = lines.get(i);
                if (l.isOpener() && l.depth >= 1) {
                    collapsed.set(i);
                }
            }
        }
    }

    public int openerCount() {
        int n = 0;
        for (Line l : lines) {
            if (l.isOpener()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ Aufbau

    private void emit(@Nullable String key, NbtElement el, int depth, int parent, boolean comma, @Nullable String dimension) {
        if (lines.size() >= maxLines) {
            truncated = true;
            return;
        }
        Line line = new Line(depth, parent);
        if (key != null) {
            line.segs.add(new Seg(SnbtFormatter.key(key), Role.KEY));
            line.segs.add(new Seg(": ", Role.PUNCT));
        }
        try {
            line.coord = detectCoord(key, el, dimension);
        } catch (RuntimeException ignored) {
        }

        if (el instanceof NbtCompound c && !c.isEmpty()) {
            line.segs.add(new Seg("{", Role.PUNCT));
            int idx = add(line);
            String childDim = c.get("dimension") instanceof NbtString s ? s.value() : dimension;
            List<String> keys = new ArrayList<>(c.getKeys());
            keys.sort(null);
            for (int i = 0; i < keys.size(); i++) {
                emit(keys.get(i), c.get(keys.get(i)), depth + 1, idx, i < keys.size() - 1, childDim);
            }
            close(idx, line, "}", comma, keys.size());
        } else if (el instanceof NbtList l && !l.isEmpty() && !isInlineList(l)) {
            line.segs.add(new Seg("[", Role.PUNCT));
            int idx = add(line);
            for (int i = 0; i < l.size(); i++) {
                emit(null, l.get(i), depth + 1, idx, i < l.size() - 1, dimension);
            }
            close(idx, line, "]", comma, l.size());
        } else if (arrayLength(el) > INLINE_ARRAY) {
            String prefix = el instanceof NbtIntArray ? "[I;" : el instanceof NbtLongArray ? "[L;" : "[B;";
            line.segs.add(new Seg(prefix, Role.PUNCT));
            int idx = add(line);
            List<String> values = arrayValues(el);
            for (int start = 0; start < values.size() && lines.size() < maxLines; start += INLINE_ARRAY) {
                Line chunk = new Line(depth + 1, idx);
                int stop = Math.min(values.size(), start + INLINE_ARRAY);
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < stop; i++) {
                    sb.append(values.get(i));
                    if (i < values.size() - 1) {
                        sb.append(i < stop - 1 ? ", " : ",");
                    }
                }
                chunk.segs.add(new Seg(sb.toString(), Role.NUMBER));
                add(chunk);
            }
            if (values.size() > 0 && lines.size() >= maxLines) {
                truncated = true;
            }
            close(idx, line, "]", comma, values.size());
        } else {
            appendInline(line.segs, el);
            if (line.coord != null) {
                // erkannte Koordinate farblich hervorheben
                for (int i = 0; i < line.segs.size(); i++) {
                    if (line.segs.get(i).role() == Role.NUMBER) {
                        line.segs.set(i, new Seg(line.segs.get(i).text(), Role.COORD));
                    }
                }
            }
            if (comma) {
                line.segs.add(new Seg(",", Role.PUNCT));
            }
            add(line);
        }
    }

    private int add(Line line) {
        lines.add(line);
        return lines.size() - 1;
    }

    private void close(int openerIdx, Line opener, String closer, boolean comma, int children) {
        Line end = new Line(opener.depth, opener.parent);
        end.closing = true;
        end.segs.add(new Seg(comma ? closer + "," : closer, Role.PUNCT));
        lines.add(end);
        opener.end = lines.size() - 1;
        opener.closer = closer;
        opener.comma = comma;
        opener.children = children;
    }

    private static boolean isInlineList(NbtList list) {
        if (list.size() > INLINE_ARRAY) {
            return false;
        }
        int chars = 2;
        for (NbtElement e : list) {
            if (!(e instanceof NbtPrimitive)) {
                return false;
            }
            chars += e.toString().length() + 2;
            if (chars > INLINE_CHARS) {
                return false;
            }
        }
        return true;
    }

    private static int arrayLength(NbtElement el) {
        if (el instanceof NbtIntArray a) {
            return a.size();
        }
        if (el instanceof NbtLongArray a) {
            return a.size();
        }
        if (el instanceof NbtByteArray a) {
            return a.size();
        }
        return -1;
    }

    private static List<String> arrayValues(NbtElement el) {
        List<String> out = new ArrayList<>();
        if (el instanceof NbtIntArray a) {
            for (int v : a.getIntArray()) {
                out.add(Integer.toString(v));
            }
        } else if (el instanceof NbtLongArray a) {
            for (long v : a.getLongArray()) {
                out.add(v + "L");
            }
        } else if (el instanceof NbtByteArray a) {
            for (byte v : a.getByteArray()) {
                out.add(v + "B");
            }
        }
        return out;
    }

    private static void appendInline(List<Seg> segs, NbtElement el) {
        if (el instanceof NbtString s) {
            String v = s.value();
            if (v.length() > MAX_STRING) {
                segs.add(new Seg(NbtString.escape(v.substring(0, MAX_STRING)), Role.STRING));
                segs.add(new Seg(" …(+" + (v.length() - MAX_STRING) + " Zeichen)", Role.DIM));
            } else {
                segs.add(new Seg(NbtString.escape(v), Role.STRING));
            }
        } else if (el instanceof NbtByte b) {
            segs.add(new Seg(b.toString(), b.byteValue() == 0 || b.byteValue() == 1 ? Role.BOOLEAN : Role.NUMBER));
        } else if (el instanceof AbstractNbtNumber n) {
            segs.add(new Seg(n.toString(), Role.NUMBER));
        } else if (el instanceof NbtCompound) {
            segs.add(new Seg("{}", Role.PUNCT));
        } else if (el instanceof NbtList list) {
            segs.add(new Seg("[", Role.PUNCT));
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    segs.add(new Seg(", ", Role.PUNCT));
                }
                appendInline(segs, list.get(i));
            }
            segs.add(new Seg("]", Role.PUNCT));
        } else if (arrayLength(el) >= 0) {
            String prefix = el instanceof NbtIntArray ? "[I; " : el instanceof NbtLongArray ? "[L; " : "[B; ";
            segs.add(new Seg(prefix, Role.PUNCT));
            segs.add(new Seg(String.join(", ", arrayValues(el)), Role.NUMBER));
            segs.add(new Seg("]", Role.PUNCT));
        } else if (el instanceof AbstractNbtList list) {
            segs.add(new Seg(list.toString(), Role.TEXT));
        } else if (el != null) {
            segs.add(new Seg(el.toString(), Role.TEXT));
        }
    }

    // ------------------------------------------------------------ Koordinaten

    /** Feldnamen wie pos, flower_pos, hive_pos, Pos, sleeping_pos, home_pos … */
    public static boolean isPosKey(@Nullable String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith("pos") || k.endsWith("position");
    }

    /**
     * Erkennt Koordinaten-Tripel: Int-Arrays mit genau 3 Werten, Compounds mit
     * X/Y/Z (bzw. x/y/z) sowie Listen aus 3 Zahlen unter einem *pos-Schlüssel.
     */
    @Nullable
    public static Coord detectCoord(@Nullable String key, NbtElement el, @Nullable String dimension) {
        String label = key != null ? key : "Koordinate";
        if (el instanceof NbtIntArray a && a.size() == 3) {
            int[] v = a.getIntArray();
            return new Coord(new BlockPos(v[0], v[1], v[2]), dimension, label);
        }
        if (el instanceof NbtCompound c) {
            Optional<Integer> x = intOf(c, "X", "x");
            Optional<Integer> y = intOf(c, "Y", "y");
            Optional<Integer> z = intOf(c, "Z", "z");
            if (x.isPresent() && y.isPresent() && z.isPresent()) {
                String dim = c.get("dimension") instanceof NbtString s ? s.value() : dimension;
                return new Coord(new BlockPos(x.get(), y.get(), z.get()), dim, label);
            }
        }
        if (isPosKey(key) && el instanceof NbtList list && list.size() == 3
                && list.get(0) instanceof AbstractNbtNumber a
                && list.get(1) instanceof AbstractNbtNumber b
                && list.get(2) instanceof AbstractNbtNumber c) {
            return new Coord(new BlockPos(MathHelper.floor(a.doubleValue()), MathHelper.floor(b.doubleValue()),
                    MathHelper.floor(c.doubleValue())), dimension, label);
        }
        return null;
    }

    private static Optional<Integer> intOf(NbtCompound c, String upper, String lower) {
        if (c.get(upper) instanceof AbstractNbtNumber n) {
            return Optional.of(n.intValue());
        }
        if (c.get(lower) instanceof AbstractNbtNumber n) {
            return Optional.of(n.intValue());
        }
        return Optional.empty();
    }
}
