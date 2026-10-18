package com.adhdclient.casino.core;

/**
 * Glücksfaktor-Modus (Schwierigkeitsgrad). Bestimmt, welches {@link Odds.Profile}
 * die zentrale {@link CasinoRng} an alle Spiele ausgibt.
 */
public enum LuckMode {
    /** Faire Spiele: erwartete Auszahlung ≈ 100 % des Einsatzes. */
    FAIR("fair"),
    /** Realistischer Casino-Hausvorteil von wenigen Prozent. */
    HOUSE("house"),
    /** Deutlich erhöhter Hausvorteil – für Hartgesottene. */
    HARDCORE("hardcore");

    private final String id;

    LuckMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "luckycasino.luck." + id;
    }

    public String descriptionKey() {
        return "luckycasino.luck." + id + ".desc";
    }

    /** Nächster Modus (für den Umschalt-Button). */
    public LuckMode next() {
        LuckMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
