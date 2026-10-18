package com.adhdclient.casino.core.progress;

/** Statistik eines einzelnen Spiels. Öffentliche Felder für die JSON-Speicherung. */
public class GameStats {
    public long rounds;
    public long wins;
    public long losses;
    public long pushes;
    public long wagered;
    public long paidOut;
    public long biggestWin;

    /** Anteil gewonnener Runden (0–1). */
    public double winRate() {
        return rounds == 0 ? 0 : (double) wins / rounds;
    }

    /** Netto-Ergebnis (Auszahlungen − Einsätze). */
    public long net() {
        return paidOut - wagered;
    }
}
