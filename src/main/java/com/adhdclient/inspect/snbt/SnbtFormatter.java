package com.adhdclient.inspect.snbt;

import net.minecraft.nbt.AbstractNbtList;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Eingerücktes, gültiges SNBT für die Zwischenablage. Vanillas
 * NbtHelper.toFormattedString zeigt Int-Arrays als Hex – für Koordinaten
 * unbrauchbar –, deshalb ein eigener Formatter.
 */
public final class SnbtFormatter {

    static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String INDENT = "  ";
    private static final int INLINE_LIMIT = 72;

    private SnbtFormatter() {
    }

    public static String format(NbtElement element) {
        StringBuilder sb = new StringBuilder();
        write(sb, element, 0);
        return sb.toString();
    }

    static String key(String key) {
        return SIMPLE_KEY.matcher(key).matches() ? key : NbtString.escape(key);
    }

    private static void write(StringBuilder sb, NbtElement element, int depth) {
        if (element instanceof NbtCompound compound) {
            if (compound.isEmpty()) {
                sb.append("{}");
                return;
            }
            List<String> keys = new ArrayList<>(compound.getKeys());
            keys.sort(null);
            sb.append("{\n");
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                indent(sb, depth + 1).append(key(key)).append(": ");
                write(sb, compound.get(key), depth + 1);
                sb.append(i < keys.size() - 1 ? ",\n" : "\n");
            }
            indent(sb, depth).append('}');
        } else if (element instanceof NbtIntArray array) {
            sb.append("[I; ");
            int[] v = array.getIntArray();
            for (int i = 0; i < v.length; i++) {
                sb.append(i > 0 ? ", " : "").append(v[i]);
            }
            sb.append(']');
        } else if (element instanceof NbtByteArray array) {
            sb.append("[B; ");
            byte[] v = array.getByteArray();
            for (int i = 0; i < v.length; i++) {
                sb.append(i > 0 ? ", " : "").append(v[i]).append('B');
            }
            sb.append(']');
        } else if (element instanceof NbtLongArray array) {
            sb.append("[L; ");
            long[] v = array.getLongArray();
            for (int i = 0; i < v.length; i++) {
                sb.append(i > 0 ? ", " : "").append(v[i]).append('L');
            }
            sb.append(']');
        } else if (element instanceof NbtList list) {
            writeList(sb, list, depth);
        } else if (element instanceof AbstractNbtList list) {
            sb.append(list);
        } else if (element != null) {
            sb.append(element); // Zahlen und Strings: Vanilla-SNBT
        }
    }

    private static void writeList(StringBuilder sb, NbtList list, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        boolean simple = true;
        for (NbtElement child : list) {
            if (child instanceof NbtCompound || child instanceof AbstractNbtList) {
                simple = false;
                break;
            }
        }
        if (simple) {
            String inline = list.toString();
            if (inline.length() <= INLINE_LIMIT) {
                sb.append(inline);
                return;
            }
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, depth + 1);
            write(sb, list.get(i), depth + 1);
            sb.append(i < list.size() - 1 ? ",\n" : "\n");
        }
        indent(sb, depth).append(']');
    }

    private static StringBuilder indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append(INDENT);
        }
        return sb;
    }
}
