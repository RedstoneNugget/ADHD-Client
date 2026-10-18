package com.adhdclient.inspect.data;

import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

/** Ein Eintrag in einem Panel-Abschnitt. Alle Varianten sind unveränderlich und damit thread-sicher. */
public sealed interface Entry {

    /** Schlüssel: Wert, optional mit Koordinate. */
    record Kv(String key, String value, Role role, @Nullable Coord coord) implements Entry {
    }

    /** Freitext / Hinweis / Warnung. */
    record Note(String text, Role role) implements Entry {
    }

    /** Zwischenüberschrift innerhalb eines Abschnitts (z. B. "Biene 1"). */
    record Sub(String title) implements Entry {
    }

    /** Vollständiger SNBT-Baum (einklappbar, hervorgehoben). */
    record Snbt(NbtElement nbt) implements Entry {
    }
}
