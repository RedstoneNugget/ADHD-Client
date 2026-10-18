package com.adhdclient.casino.core.progress;

import com.adhdclient.casino.core.Odds;
import com.adhdclient.casino.core.loot.Cosmetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kompletter Spielstand (Guthaben, Statistiken, Rekorde, Erfolge, Kosmetik).
 * Öffentliche Felder, damit Gson ihn direkt (de)serialisieren kann.
 */
public class PlayerProfile {
    /** Maximale Länge des Guthaben-Verlaufs für das Diagramm. */
    public static final int HISTORY_LIMIT = 500;

    public long balance = Odds.START_BALANCE;
    /** Zuletzt eingestellter Einsatz (gilt für alle Spiele). */
    public long bet = Odds.DEFAULT_BET;
    /** Zeitpunkt des letzten Notkredits (Epoch-ms, 0 = nie). */
    public long lastLoanAt;
    /** Datum (ISO, yyyy-MM-dd) der letzten Glücksrad-Drehung. */
    public String lastWheelDay = "";
    /** Verfügbare Slot-Freispiele. */
    public int freeSpins;
    /** Bonus-Multiplikator für den Reingewinn des nächsten Gewinns (1 = keiner). */
    public int bonusMultiplier = 1;

    public Statistics stats = new Statistics();
    public Records records = new Records();
    /** Freigeschaltete Erfolge: ID → Zeitpunkt. */
    public Map<String, Long> achievements = new LinkedHashMap<>();
    /** Besessene Kosmetik (Schlüssel). */
    public Set<String> cosmetics = new LinkedHashSet<>();
    public String equippedTitle = "";
    public String equippedFrame = Cosmetic.FRAME_CLASSIC.key();
    /** Guthaben-Verlauf nach jeder Runde. */
    public List<Long> balanceHistory = new ArrayList<>();

    /** Casino-Level und XP innerhalb des aktuellen Levels. */
    public int level = 1;
    public long xp;
    /** Datum (ISO) der aktuellen Tagesmissionen und deren Fortschritt. */
    public String missionDay = "";
    public List<Missions.Progress> missions = new ArrayList<>();
    public int missionsCompleted;
    /** Interne Markierungen (bereits eingelöste Boni). */
    public List<String> flags = new ArrayList<>();

    /**
     * Repariert unvollständige oder manipulierte Daten nach dem Laden
     * (fehlende Felder, null-Werte, negative Zahlen, unbekannte Schlüssel).
     */
    public void sanitize() {
        balance = Math.max(0, balance);
        bet = Math.max(Odds.MIN_BET, Math.min(bet, Odds.MAX_BET));
        lastLoanAt = Math.max(0, lastLoanAt);
        if (lastWheelDay == null) {
            lastWheelDay = "";
        }
        freeSpins = Math.max(0, Math.min(freeSpins, 1_000));
        bonusMultiplier = Math.max(1, Math.min(bonusMultiplier, 10));

        if (stats == null) {
            stats = new Statistics();
        }
        if (stats.games == null) {
            stats.games = new LinkedHashMap<>();
        }
        stats.games.values().removeIf(g -> g == null);
        stats.playTimeMs = Math.max(0, stats.playTimeMs);

        if (records == null) {
            records = new Records();
        }
        if (records.topWins == null) {
            records.topWins = new ArrayList<>();
        }
        records.topWins.removeIf(w -> w == null || w.game == null);
        if (records.biggestWinGame == null) {
            records.biggestWinGame = "";
        }
        records.highestBalance = Math.max(records.highestBalance, balance);

        if (achievements == null) {
            achievements = new LinkedHashMap<>();
        }
        achievements.keySet().removeIf(id -> id == null || !isKnownAchievement(id));

        if (cosmetics == null) {
            cosmetics = new LinkedHashSet<>();
        }
        cosmetics.removeIf(k -> k == null || Cosmetic.byKey(k) == null);
        cosmetics.add(Cosmetic.FRAME_CLASSIC.key());
        if (equippedTitle == null || (!equippedTitle.isEmpty() && !cosmetics.contains(equippedTitle))) {
            equippedTitle = "";
        }
        if (equippedFrame == null || !cosmetics.contains(equippedFrame)) {
            equippedFrame = Cosmetic.FRAME_CLASSIC.key();
        }

        level = Math.max(1, Math.min(level, 10_000));
        xp = Math.max(0, xp);
        missionsCompleted = Math.max(0, missionsCompleted);
        if (missionDay == null) {
            missionDay = "";
        }
        if (flags == null) {
            flags = new ArrayList<>();
        }
        flags.removeIf(f -> f == null);
        if (missions == null) {
            missions = new ArrayList<>();
        }
        missions.removeIf(m -> m == null || Missions.byId(m.id) == null);
        if (missions.size() != Missions.PER_DAY) {
            missions.clear();
            missionDay = ""; // neu erzeugen lassen
        }

        if (balanceHistory == null) {
            balanceHistory = new ArrayList<>();
        }
        balanceHistory.removeIf(v -> v == null);
        if (balanceHistory.isEmpty()) {
            balanceHistory.add(balance);
        }
        while (balanceHistory.size() > HISTORY_LIMIT) {
            balanceHistory.removeFirst();
        }
    }

    private static boolean isKnownAchievement(String id) {
        for (Achievement a : Achievement.values()) {
            if (a.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
