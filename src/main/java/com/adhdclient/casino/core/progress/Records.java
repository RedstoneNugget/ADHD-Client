package com.adhdclient.casino.core.progress;

import com.adhdclient.casino.core.Odds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Persönliche Rekorde (lokale Bestenliste). */
public class Records {
    public static final int TOP_WINS = 10;

    public long highestBalance = Odds.START_BALANCE;
    public int longestWinStreak;
    public int currentWinStreak;
    public long biggestWin;
    public String biggestWinGame = "";
    public double bestCrashMultiplier;
    public int longestCoinStreak;
    public List<WinEntry> topWins = new ArrayList<>();

    /** Trägt einen Gewinn in die Top-Liste ein (absteigend sortiert, max. {@link #TOP_WINS}). */
    public void addWin(WinEntry entry) {
        topWins.add(entry);
        topWins.sort(Comparator.comparingLong((WinEntry w) -> w.profit).reversed());
        while (topWins.size() > TOP_WINS) {
            topWins.removeLast();
        }
    }
}
