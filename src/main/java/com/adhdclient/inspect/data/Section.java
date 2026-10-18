package com.adhdclient.inspect.data;

import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Ein Abschnitt im Info-Panel mit Builder-Hilfen. */
public final class Section {

    /** Text für Daten, die ein Server nicht an den Client schickt. */
    public static final String NOT_SENT = "Vom Server nicht übertragen";
    public static final String NOT_SENT_EN = "Not sent by the server";

    public static String notSentText() {
        return com.adhdclient.gui.L.t(NOT_SENT, NOT_SENT_EN);
    }

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?[bBsSlLfFdD]?( ?(%|Ticks|ticks|s|m|Blöcke|blocks))?");

    public final Category category;
    public final String title;
    public final List<Entry> entries = new ArrayList<>();
    public boolean collapsedByDefault;

    public Section(Category category, String title) {
        this.category = category;
        this.title = title;
    }

    public Section kv(String key, @Nullable Object value) {
        String text = value == null ? "–" : String.valueOf(value);
        entries.add(new Entry.Kv(key, text, guessRole(text), null));
        return this;
    }

    public Section kv(String key, String value, Role role) {
        entries.add(new Entry.Kv(key, value, role, null));
        return this;
    }

    public Section notSent(String key) {
        entries.add(new Entry.Kv(key, notSentText(), Role.WARN, null));
        return this;
    }

    public Section coord(String key, BlockPos pos, @Nullable String dimension, String label) {
        Coord c = new Coord(pos.toImmutable(), dimension, label);
        entries.add(new Entry.Kv(key, c.text(), Role.COORD, c));
        return this;
    }

    public Section coord(String key, String value, BlockPos pos, String label) {
        entries.add(new Entry.Kv(key, value, Role.COORD, new Coord(pos.toImmutable(), null, label)));
        return this;
    }

    public Section note(String text) {
        entries.add(new Entry.Note(text, Role.TEXT));
        return this;
    }

    public Section dim(String text) {
        entries.add(new Entry.Note(text, Role.DIM));
        return this;
    }

    public Section warn(String text) {
        entries.add(new Entry.Note(text, Role.WARN));
        return this;
    }

    public Section sub(String title) {
        entries.add(new Entry.Sub(title));
        return this;
    }

    public Section snbt(NbtElement nbt) {
        entries.add(new Entry.Snbt(nbt));
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static String yesNo(boolean b) {
        return b ? com.adhdclient.gui.L.t("ja", "yes") : com.adhdclient.gui.L.t("nein", "no");
    }

    static Role guessRole(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("ja") || lower.equals("nein") || lower.equals("yes") || lower.equals("no") || lower.equals("true") || lower.equals("false")) {
            return Role.BOOLEAN;
        }
        if (text.equals(NOT_SENT) || text.equals(NOT_SENT_EN)) {
            return Role.WARN;
        }
        if (NUMBER.matcher(text).matches()) {
            return Role.NUMBER;
        }
        return Role.TEXT;
    }
}
