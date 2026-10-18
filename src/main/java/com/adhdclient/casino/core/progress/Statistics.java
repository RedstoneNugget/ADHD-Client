package com.adhdclient.casino.core.progress;

import com.adhdclient.casino.core.GameType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Gesamtstatistik. Spiele werden über ihren Enum-Namen gespeichert (robust gegen Änderungen). */
public class Statistics {
    public Map<String, GameStats> games = new LinkedHashMap<>();
    /** Zeit mit geöffnetem Casino-Fenster. */
    public long playTimeMs;
    /** Chips aus Notkredit, Glücksrad und Lootboxen. */
    public long bonusIncome;
    /** Für Lootboxen ausgegebene Chips. */
    public long shopSpending;
    public long loansTaken;
    public long wheelSpins;
    public long lootBoxesOpened;

    public GameStats get(GameType type) {
        return games.computeIfAbsent(type.name(), k -> new GameStats());
    }

    public long totalRounds() {
        return games.values().stream().mapToLong(g -> g.rounds).sum();
    }

    public long totalWins() {
        return games.values().stream().mapToLong(g -> g.wins).sum();
    }

    public long totalLosses() {
        return games.values().stream().mapToLong(g -> g.losses).sum();
    }

    public long totalWagered() {
        return games.values().stream().mapToLong(g -> g.wagered).sum();
    }

    public long totalPaidOut() {
        return games.values().stream().mapToLong(g -> g.paidOut).sum();
    }

    /** Netto-Ergebnis aller Spiele. */
    public long netResult() {
        return totalPaidOut() - totalWagered();
    }

    public double winRate() {
        long rounds = totalRounds();
        return rounds == 0 ? 0 : (double) totalWins() / rounds;
    }

    /** Wie viele verschiedene Spiele wurden schon gespielt? */
    public int gamesPlayed() {
        int n = 0;
        for (GameType t : GameType.values()) {
            GameStats g = games.get(t.name());
            if (g != null && g.rounds > 0) {
                n++;
            }
        }
        return n;
    }
}
