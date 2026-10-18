package com.adhdclient.casino.core;

/**
 * Hilfsfunktionen für die Einsatz-Steuerung (+10, +100, x2, ½, Max, Eingabefeld).
 * Ergebnis liegt immer zwischen {@link Odds#MIN_BET} und dem verfügbaren Maximum.
 */
public final class BetMath {

    private BetMath() {
    }

    /** Größter möglicher Einsatz bei diesem Guthaben (mindestens MIN_BET, damit das Feld nie leer ist). */
    public static long maxBet(long balance) {
        return Math.max(Odds.MIN_BET, Math.min(balance, Odds.MAX_BET));
    }

    public static long clamp(long bet, long balance) {
        return Math.max(Odds.MIN_BET, Math.min(bet, maxBet(balance)));
    }

    public static long add(long bet, long amount, long balance) {
        return clamp(saturatedAdd(bet, amount), balance);
    }

    public static long doubled(long bet, long balance) {
        return clamp(saturatedAdd(bet, bet), balance);
    }

    public static long halved(long bet, long balance) {
        return clamp(bet / 2, balance);
    }

    /**
     * Liest eine Eingabe wie "1.500", "1 500" oder "2k". Liefert -1 bei ungültiger Eingabe.
     */
    public static long parse(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.trim().toLowerCase().replace(".", "").replace(",", "").replace(" ", "").replace("_", "");
        if (t.isEmpty()) {
            return -1;
        }
        long factor = 1;
        if (t.endsWith("k")) {
            factor = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            factor = 1_000_000;
            t = t.substring(0, t.length() - 1);
        }
        if (t.isEmpty() || t.length() > 12 || !t.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Long.parseLong(t) * factor;
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
