package com.adhdclient.casino.core.progress;

/** Ein Eintrag der lokalen Bestenliste (größte Einzelgewinne). */
public class WinEntry {
    /** Enum-Name des Spiels. */
    public String game;
    /** Reingewinn (Auszahlung − Einsatz). */
    public long profit;
    public long stake;
    /** Zeitpunkt (Epoch-ms). */
    public long time;

    public WinEntry() {
    }

    public WinEntry(String game, long profit, long stake, long time) {
        this.game = game;
        this.profit = profit;
        this.stake = stake;
        this.time = time;
    }
}
