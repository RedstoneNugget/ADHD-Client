package com.adhdclient.casino.core.roulette;

import com.adhdclient.casino.core.CasinoRng;

import java.util.Map;

/**
 * Roulette-Grundlagen: Farben, Beschriftungen, Drehen und Auswerten.
 */
public final class Roulette {

    /** Interne Darstellung des Fachs "00" (amerikanisches Roulette). */
    public static final int DOUBLE_ZERO = 37;

    private static final boolean[] RED = new boolean[38];

    static {
        for (int n : new int[]{1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) {
            RED[n] = true;
        }
    }

    public enum Color { RED, BLACK, GREEN }

    private Roulette() {
    }

    public static Color colorOf(int number) {
        if (number == 0 || number == DOUBLE_ZERO) {
            return Color.GREEN;
        }
        return RED[number] ? Color.RED : Color.BLACK;
    }

    public static boolean isZero(int number) {
        return number == 0 || number == DOUBLE_ZERO;
    }

    public static String label(int number) {
        return number == DOUBLE_ZERO ? "00" : Integer.toString(number);
    }

    /** Dreht den Kessel des aktuellen Modus und liefert die gefallene Zahl. */
    public static int spin(CasinoRng rng) {
        RouletteVariant variant = rng.odds().rouletteVariant();
        return variant.pocketAt(rng.nextInt(variant.pocketCount()));
    }

    /** Summe der Auszahlungen aller Wetten für die gefallene Zahl. */
    public static long payout(Map<RouletteBet, Long> bets, int pocket) {
        long total = 0;
        for (Map.Entry<RouletteBet, Long> e : bets.entrySet()) {
            if (e.getKey().wins(pocket)) {
                total += e.getValue() * e.getKey().kind().payout();
            }
        }
        return total;
    }
}
